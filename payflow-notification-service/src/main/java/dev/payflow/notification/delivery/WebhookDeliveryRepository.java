package dev.payflow.notification.delivery;

import java.sql.Types;
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
    updateOutcome(deliveryId, "DELIVERED", responseCode);
  }

  public void markFailed(UUID deliveryId, Integer responseCode) {
    updateOutcome(deliveryId, "FAILED", responseCode);
  }

  private void updateOutcome(UUID deliveryId, String status, Integer responseCode) {
    jdbc.update(
        """
        UPDATE webhook_deliveries
        SET status = :status, response_code = :responseCode,
            attempt_count = attempt_count + 1, last_attempt_at = now()
        WHERE id = :id
        """,
        new MapSqlParameterSource()
            .addValue("status", status)
            .addValue("responseCode", responseCode, Types.INTEGER)
            .addValue("id", deliveryId));
  }
}
