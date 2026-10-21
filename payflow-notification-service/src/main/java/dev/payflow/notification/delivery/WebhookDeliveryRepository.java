package dev.payflow.notification.delivery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WebhookDeliveryRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public WebhookDeliveryRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public UUID insertDelivery(UUID endpointId, String eventType, String payload) {
    return jdbc.queryForObject(
        """
        INSERT INTO webhook_deliveries (endpoint_id, event_type, payload, status)
        VALUES (:endpointId, :eventType, CAST(:payload AS jsonb), 'PENDING')
        RETURNING id
        """,
        new MapSqlParameterSource()
            .addValue("endpointId", endpointId)
            .addValue("eventType", eventType)
            .addValue("payload", payload),
        UUID.class);
  }

  public void markDelivered(UUID deliveryId, Integer responseCode) {
    jdbc.update(
        """
        UPDATE webhook_deliveries
        SET status = 'DELIVERED', response_code = :responseCode,
            attempt_count = attempt_count + 1, last_attempt_at = now(), next_retry_at = NULL
        WHERE id = :id
        """,
        new MapSqlParameterSource().addValue("responseCode", responseCode, Types.INTEGER).addValue("id", deliveryId));
  }

  /** Increments {@code attempt_count} and records the outcome, returning the new attempt count. */
  public int recordAttemptFailure(UUID deliveryId, Integer responseCode) {
    return jdbc.queryForObject(
        """
        UPDATE webhook_deliveries
        SET response_code = :responseCode, attempt_count = attempt_count + 1, last_attempt_at = now()
        WHERE id = :id
        RETURNING attempt_count
        """,
        new MapSqlParameterSource().addValue("responseCode", responseCode, Types.INTEGER).addValue("id", deliveryId),
        Integer.class);
  }

  public void scheduleRetry(UUID deliveryId, Instant nextRetryAt) {
    jdbc.update(
        "UPDATE webhook_deliveries SET status = 'PENDING', next_retry_at = :nextRetryAt WHERE id = :id",
        new MapSqlParameterSource()
            .addValue("nextRetryAt", Timestamp.from(nextRetryAt))
            .addValue("id", deliveryId));
  }

  public void markDeadLetter(UUID deliveryId) {
    jdbc.update(
        "UPDATE webhook_deliveries SET status = 'DEAD_LETTER', next_retry_at = NULL WHERE id = :id",
        new MapSqlParameterSource().addValue("id", deliveryId));
  }

  public void resetForRetry(UUID deliveryId) {
    jdbc.update(
        "UPDATE webhook_deliveries SET attempt_count = 0, status = 'PENDING', next_retry_at = NULL WHERE id = :id",
        new MapSqlParameterSource().addValue("id", deliveryId));
  }

  public List<WebhookDelivery> findDeadLetter() {
    return jdbc.query(
        "SELECT * FROM webhook_deliveries WHERE status = 'DEAD_LETTER' ORDER BY last_attempt_at DESC",
        this::mapRow);
  }

  /** BE-08: the merchant-facing delivery log — every delivery (success and failure), not just the DLQ. */
  public List<WebhookDelivery> findByMerchantId(UUID merchantId, int offset, int limit) {
    return jdbc.query(
        """
        SELECT d.* FROM webhook_deliveries d
        JOIN webhook_endpoints e ON d.endpoint_id = e.id
        WHERE e.merchant_id = :merchantId
        ORDER BY d.last_attempt_at DESC NULLS LAST
        LIMIT :limit OFFSET :offset
        """,
        new MapSqlParameterSource()
            .addValue("merchantId", merchantId)
            .addValue("limit", limit)
            .addValue("offset", offset),
        this::mapRow);
  }

  public long countByMerchantId(UUID merchantId) {
    return jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM webhook_deliveries d
        JOIN webhook_endpoints e ON d.endpoint_id = e.id
        WHERE e.merchant_id = :merchantId
        """,
        new MapSqlParameterSource().addValue("merchantId", merchantId),
        Long.class);
  }

  public Optional<WebhookDelivery> findById(UUID deliveryId) {
    List<WebhookDelivery> rows =
        jdbc.query(
            "SELECT * FROM webhook_deliveries WHERE id = :id",
            new MapSqlParameterSource().addValue("id", deliveryId),
            this::mapRow);
    return rows.stream().findFirst();
  }

  private WebhookDelivery mapRow(ResultSet rs, int rowNum) throws SQLException {
    String endpointId = rs.getString("endpoint_id");
    Timestamp lastAttemptAt = rs.getTimestamp("last_attempt_at");
    Timestamp nextRetryAt = rs.getTimestamp("next_retry_at");
    Object responseCode = rs.getObject("response_code");
    return new WebhookDelivery(
        UUID.fromString(rs.getString("id")),
        endpointId != null ? UUID.fromString(endpointId) : null,
        rs.getString("event_type"),
        rs.getString("payload"),
        rs.getString("status"),
        rs.getInt("attempt_count"),
        responseCode != null ? ((Number) responseCode).intValue() : null,
        lastAttemptAt != null ? lastAttemptAt.toInstant() : null,
        nextRetryAt != null ? nextRetryAt.toInstant() : null);
  }
}
