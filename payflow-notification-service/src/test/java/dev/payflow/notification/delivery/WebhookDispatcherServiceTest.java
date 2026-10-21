package dev.payflow.notification.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.payflow.notification.AbstractPostgresIntegrationTest;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@SpringBootTest
class WebhookDispatcherServiceTest extends AbstractPostgresIntegrationTest {

  private static final String UNREACHABLE_URL = "http://127.0.0.1:1/webhook";

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
  void dispatchingToAnUnreachableEndpointSchedulesARetryWithBackoff() {
    UUID merchantId = UUID.randomUUID();
    insertActiveEndpoint(merchantId, UNREACHABLE_URL);

    dispatcherService.dispatch(merchantId, "payment.refunded", "{\"paymentId\":\"xyz\"}");

    var row = findLatestDeliveryFor("payment.refunded");
    assertThat(row.get("status")).isEqualTo("PENDING");
    assertThat(row.get("endpoint_id")).isNotNull();
    assertThat(((Number) row.get("attempt_count")).intValue()).isEqualTo(1);
    assertThat(row.get("next_retry_at")).isNotNull();
  }

  @Test
  void backoffFollowsTheSpecifiedScheduleForEachRetryableAttempt() {
    assertThat(WebhookDispatcherService.backoffFor(1)).isEqualTo(Duration.ofSeconds(1));
    assertThat(WebhookDispatcherService.backoffFor(2)).isEqualTo(Duration.ofSeconds(5));
    assertThat(WebhookDispatcherService.backoffFor(3)).isEqualTo(Duration.ofSeconds(30));
    assertThat(WebhookDispatcherService.backoffFor(4)).isEqualTo(Duration.ofMinutes(2));
  }

  @Test
  void aDeliveryIsMovedToDeadLetterAfterFiveFailedAttempts() {
    UUID merchantId = UUID.randomUUID();
    insertActiveEndpoint(merchantId, UNREACHABLE_URL);
    dispatcherService.dispatch(merchantId, "payment.dlq-demo", "{\"paymentId\":\"dlq-1\"}");
    var row = findLatestDeliveryFor("payment.dlq-demo");
    UUID deliveryId = (UUID) row.get("id");
    WebhookEndpoint endpoint =
        new WebhookEndpoint((UUID) row.get("endpoint_id"), merchantId, UNREACHABLE_URL, "test-secret", true);

    // Attempt #1 already ran via dispatch() above; simulate 4 more failed attempts directly
    // rather than waiting out the real backoff delays between them.
    for (int i = 0; i < 4; i++) {
      dispatcherService.attemptDelivery(deliveryId, endpoint, "{\"paymentId\":\"dlq-1\"}");
    }

    row = findLatestDeliveryFor("payment.dlq-demo");
    assertThat(row.get("status")).isEqualTo("DEAD_LETTER");
    assertThat(((Number) row.get("attempt_count")).intValue()).isEqualTo(5);
    assertThat(row.get("next_retry_at")).isNull();
  }

  @Test
  void retryDeliveryResetsAttemptCountAndReattemptsImmediately() {
    UUID merchantId = UUID.randomUUID();
    insertActiveEndpoint(merchantId, UNREACHABLE_URL);
    dispatcherService.dispatch(merchantId, "payment.retry-demo", "{\"paymentId\":\"retry-1\"}");
    var row = findLatestDeliveryFor("payment.retry-demo");
    UUID deliveryId = (UUID) row.get("id");
    jdbc.update(
        "UPDATE webhook_deliveries SET attempt_count = 5, status = 'DEAD_LETTER', next_retry_at = NULL WHERE id = :id",
        new MapSqlParameterSource().addValue("id", deliveryId));

    dispatcherService.retryDelivery(deliveryId);

    row = findLatestDeliveryFor("payment.retry-demo");
    assertThat(((Number) row.get("attempt_count")).intValue()).isEqualTo(1);
    assertThat(row.get("status")).isEqualTo("PENDING");
  }

  @Test
  void retryDeliveryThrowsForAnUnknownDeliveryId() {
    assertThatThrownBy(() -> dispatcherService.retryDelivery(UUID.randomUUID()))
        .isInstanceOf(NoSuchElementException.class);
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
