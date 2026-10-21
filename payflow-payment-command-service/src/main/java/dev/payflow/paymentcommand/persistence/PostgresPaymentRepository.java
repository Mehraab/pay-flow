package dev.payflow.paymentcommand.persistence;

import dev.payflow.common.domain.Currency;
import dev.payflow.common.domain.Money;
import dev.payflow.common.domain.PaymentStatus;
import dev.payflow.cqrs.exception.ConcurrencyException;
import dev.payflow.cqrs.persistence.AggregateRepository;
import dev.payflow.paymentcommand.domain.Payment;
import dev.payflow.paymentcommand.domain.PaymentAuthorized;
import dev.payflow.paymentcommand.domain.PaymentCaptured;
import dev.payflow.paymentcommand.domain.PaymentCreated;
import dev.payflow.paymentcommand.domain.PaymentDomainEvent;
import dev.payflow.paymentcommand.domain.PaymentFailed;
import dev.payflow.paymentcommand.domain.PaymentId;
import dev.payflow.paymentcommand.domain.PaymentRefunded;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * The Transactional Outbox pattern in one method: {@link #save} writes the aggregate row and its
 * outbox rows in the same database transaction, so a crash between them is impossible
 * (ARCHITECTURE_AND_PLAN.md §1.2). Optimistic locking is a plain UPDATE...WHERE id AND version —
 * when that affects zero rows, a follow-up INSERT ... ON CONFLICT (id) DO NOTHING distinguishes
 * "this aggregate doesn't exist yet" (insert succeeds) from "someone else updated it first"
 * (insert also affects zero rows, because the row already exists) without a separate SELECT in
 * the common case.
 */
@Repository
public class PostgresPaymentRepository
    implements AggregateRepository<Payment, PaymentId, PaymentDomainEvent> {

  private static final String AGGREGATE_TYPE = "PAYMENT";

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public PostgresPaymentRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<Payment> findById(PaymentId id) {
    List<Payment> rows =
        jdbc.query(
            """
            SELECT id, merchant_id, amount_minor, currency, status, version,
                   idempotency_key, provider_ref, decline_reason
            FROM payments
            WHERE id = :id
            """,
            new MapSqlParameterSource("id", id.value()),
            this::mapRow);
    return rows.stream().findFirst();
  }

  @Override
  @Transactional
  public void save(Payment payment) {
    long expectedVersion = payment.version();
    long newVersion = expectedVersion + 1;

    int updated =
        jdbc.update(
            """
            UPDATE payments
            SET merchant_id = :merchantId, amount_minor = :amountMinor, currency = :currency,
                status = :status, version = :newVersion, idempotency_key = :idempotencyKey,
                provider_ref = :providerRef, decline_reason = :declineReason, updated_at = now()
            WHERE id = :id AND version = :expectedVersion
            """,
            paramsFor(payment, newVersion).addValue("expectedVersion", expectedVersion));

    if (updated == 0) {
      int inserted =
          jdbc.update(
              """
              INSERT INTO payments
                  (id, merchant_id, amount_minor, currency, status, version,
                   idempotency_key, provider_ref, decline_reason)
              VALUES
                  (:id, :merchantId, :amountMinor, :currency, :status, :newVersion,
                   :idempotencyKey, :providerRef, :declineReason)
              ON CONFLICT (id) DO NOTHING
              """,
              paramsFor(payment, newVersion));

      if (inserted == 0) {
        Optional<Payment> current = findById(payment.id());
        long actualVersion = current.map(Payment::version).orElse(-1L);
        throw new ConcurrencyException(payment.id(), expectedVersion, actualVersion);
      }
    }

    payment.incrementVersion();

    for (PaymentDomainEvent event : payment.pullUncommittedEvents()) {
      insertOutboxEvent(payment.id(), event);
    }
  }

  private MapSqlParameterSource paramsFor(Payment payment, long newVersion) {
    return new MapSqlParameterSource()
        .addValue("id", payment.id().value())
        .addValue("merchantId", payment.merchantId())
        .addValue("amountMinor", payment.amount().amountMinor())
        .addValue("currency", payment.amount().currency().name())
        .addValue("status", payment.status().name())
        .addValue("newVersion", newVersion)
        .addValue("idempotencyKey", payment.idempotencyKey())
        .addValue("providerRef", payment.providerRef())
        .addValue("declineReason", payment.declineReason());
  }

  private void insertOutboxEvent(PaymentId aggregateId, PaymentDomainEvent event) {
    String eventType = eventTypeOf(event);
    String payloadJson = objectMapper.writeValueAsString(event);

    jdbc.update(
        """
        INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload)
        VALUES (:aggregateType, :aggregateId, :eventType, CAST(:payload AS jsonb))
        """,
        new MapSqlParameterSource()
            .addValue("aggregateType", AGGREGATE_TYPE)
            .addValue("aggregateId", aggregateId.value())
            .addValue("eventType", eventType)
            .addValue("payload", payloadJson));
  }

  private String eventTypeOf(PaymentDomainEvent event) {
    return switch (event) {
      case PaymentCreated e -> "payment.created";
      case PaymentAuthorized e -> "payment.authorized";
      case PaymentCaptured e -> "payment.captured";
      case PaymentFailed e -> "payment.failed";
      case PaymentRefunded e -> "payment.refunded";
    };
  }

  private Payment mapRow(ResultSet rs, int rowNum) throws SQLException {
    return Payment.reconstitute(
        new PaymentId(UUID.fromString(rs.getString("id"))),
        rs.getLong("version"),
        UUID.fromString(rs.getString("merchant_id")),
        Money.of(rs.getLong("amount_minor"), Currency.valueOf(rs.getString("currency"))),
        PaymentStatus.valueOf(rs.getString("status")),
        rs.getString("idempotency_key"),
        rs.getString("provider_ref"),
        rs.getString("decline_reason"));
  }
}
