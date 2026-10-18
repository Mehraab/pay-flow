package dev.payflow.paymentcommand.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.payflow.common.domain.Currency;
import dev.payflow.common.domain.Money;
import dev.payflow.common.domain.PaymentStatus;
import dev.payflow.cqrs.exception.AggregateNotFoundException;
import dev.payflow.cqrs.exception.ConcurrencyException;
import dev.payflow.paymentcommand.AbstractPostgresIntegrationTest;
import dev.payflow.paymentcommand.domain.Payment;
import dev.payflow.paymentcommand.domain.PaymentId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@SpringBootTest
class PostgresPaymentRepositoryTest extends AbstractPostgresIntegrationTest {

  @Autowired private PostgresPaymentRepository repository;
  @Autowired private NamedParameterJdbcTemplate jdbc;

  private final UUID merchantId = UUID.randomUUID();
  private final Money amount = Money.of(21400, Currency.USD);

  @Test
  void savingANewAggregateInsertsItAndItsOutboxEvent() {
    Payment payment = Payment.create(PaymentId.generate(), merchantId, amount, uniqueKey());

    repository.save(payment);

    Payment loaded = repository.findById(payment.id()).orElseThrow();
    assertThat(loaded.status()).isEqualTo(PaymentStatus.CREATED);
    assertThat(loaded.merchantId()).isEqualTo(merchantId);
    assertThat(loaded.amount()).isEqualTo(amount);
    assertThat(loaded.version()).isEqualTo(1);

    Integer outboxCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM outbox_events WHERE aggregate_id = :id AND event_type = 'payment.created'",
            new MapSqlParameterSource("id", payment.id().value()),
            Integer.class);
    assertThat(outboxCount).isEqualTo(1);
  }

  @Test
  void savingAMutatedAggregateUpdatesItAndAppendsAnotherOutboxEvent() {
    Payment payment = Payment.create(PaymentId.generate(), merchantId, amount, uniqueKey());
    repository.save(payment);

    payment.capture("mock-ch-abc123");
    repository.save(payment);

    Payment loaded = repository.findById(payment.id()).orElseThrow();
    assertThat(loaded.status()).isEqualTo(PaymentStatus.CAPTURED);
    assertThat(loaded.providerRef()).isEqualTo("mock-ch-abc123");
    assertThat(loaded.version()).isEqualTo(2);

    Integer outboxCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM outbox_events WHERE aggregate_id = :id",
            new MapSqlParameterSource("id", payment.id().value()),
            Integer.class);
    assertThat(outboxCount).isEqualTo(2);
  }

  @Test
  void savingAtAStaleVersionThrowsConcurrencyException() {
    Payment payment = Payment.create(PaymentId.generate(), merchantId, amount, uniqueKey());
    repository.save(payment); // now at version 1 in the database

    Payment staleCopy =
        Payment.reconstitute(
            payment.id(), 0, merchantId, amount, PaymentStatus.CREATED, payment.idempotencyKey(), null, null);
    staleCopy.capture("mock-ch-xyz"); // still thinks it's at version 0

    assertThatThrownBy(() -> repository.save(staleCopy)).isInstanceOf(ConcurrencyException.class);
  }

  @Test
  void findByIdReturnsEmptyForAnUnknownId() {
    assertThat(repository.findById(PaymentId.generate())).isEmpty();
  }

  @Test
  void getByIdThrowsAggregateNotFoundExceptionForAnUnknownId() {
    assertThatThrownBy(() -> repository.getById(PaymentId.generate()))
        .isInstanceOf(AggregateNotFoundException.class);
  }

  @Test
  void theSameMerchantAndIdempotencyKeyCannotBeInsertedTwice() {
    String key = uniqueKey();
    Payment first = Payment.create(PaymentId.generate(), merchantId, amount, key);
    repository.save(first);

    Payment second = Payment.create(PaymentId.generate(), merchantId, amount, key);

    assertThatThrownBy(() -> repository.save(second)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  private String uniqueKey() {
    return "test-" + UUID.randomUUID();
  }
}
