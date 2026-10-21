package dev.payflow.notification.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import dev.payflow.notification.AbstractPostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@SpringBootTest
class WebhookDispatcherServiceTest extends AbstractPostgresIntegrationTest {

  @Autowired private WebhookDispatcherService dispatcherService;
  @Autowired private NamedParameterJdbcTemplate jdbc;

  @Test
  void dispatchingWithNoEndpointConfiguredMarksTheDeliveryDeliveredImmediately() {
    UUID merchantId = UUID.randomUUID();

    dispatcherService.dispatch(merchantId, "payment.captured", "{\"paymentId\":\"abc\"}");

    var row = findLatestDeliveryFor("payment.captured");
    assertThat(row.get("status")).isEqualTo("DELIVERED");
    assertThat(row.get("endpoint_id")).isNull();
    assertThat(((Number) row.get("attempt_count")).intValue()).isEqualTo(1);
  }

  @Test
  void dispatchingToAnUnreachableEndpointMarksTheDeliveryFailed() {
    UUID merchantId = UUID.randomUUID();
    insertActiveEndpoint(merchantId, "http://127.0.0.1:1/webhook");

    dispatcherService.dispatch(merchantId, "payment.refunded", "{\"paymentId\":\"xyz\"}");

    var row = findLatestDeliveryFor("payment.refunded");
    assertThat(row.get("status")).isEqualTo("FAILED");
    assertThat(row.get("endpoint_id")).isNotNull();
    assertThat(((Number) row.get("attempt_count")).intValue()).isEqualTo(1);
  }

  private void insertActiveEndpoint(UUID merchantId, String url) {
    jdbc.update(
        """
        INSERT INTO webhook_endpoints (merchant_id, url, signing_secret, active)
        VALUES (:merchantId, :url, 'test-secret', true)
        """,
        new MapSqlParameterSource().addValue("merchantId", merchantId).addValue("url", url));
  }

  private java.util.Map<String, Object> findLatestDeliveryFor(String eventType) {
    return jdbc.queryForMap(
        """
        SELECT * FROM webhook_deliveries WHERE event_type = :eventType
        ORDER BY id DESC LIMIT 1
        """,
        new MapSqlParameterSource("eventType", eventType));
  }
}
