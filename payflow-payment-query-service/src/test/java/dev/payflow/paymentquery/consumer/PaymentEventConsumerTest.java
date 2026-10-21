package dev.payflow.paymentquery.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.payflow.paymentquery.view.PaymentView;
import dev.payflow.paymentquery.view.PaymentViewStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * No Kafka broker involved — exercises the consumer method directly with the same (key,
 * event-type header, JSON payload) shape a real {@code @KafkaListener} invocation would deliver.
 */
class PaymentEventConsumerTest {

  private final PaymentViewStore store = new PaymentViewStore();
  // Matches Spring Boot's autoconfigured ObjectMapper (which the production bean actually is):
  // FAIL_ON_UNKNOWN_PROPERTIES disabled, so the extra "metadata"/"id"/"idempotencyKey" fields in
  // a real payment.created payload don't blow up PaymentCreatedPayload's deliberately-partial shape.
  private final ObjectMapper objectMapper =
      JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  private final PaymentEventConsumer consumer = new PaymentEventConsumer(store, objectMapper);

  @Test
  void paymentCreatedEventPopulatesTheViewFromTheRealEventJsonShape() {
    UUID paymentId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    String payload =
        """
        {
          "metadata": {"eventId": "evt-1", "correlationId": "corr-1"},
          "id": {"value": "%s"},
          "merchantId": "%s",
          "amount": {"amountMinor": 21400, "currency": "USD"},
          "idempotencyKey": "demo-001"
        }
        """
            .formatted(paymentId, merchantId);

    consumer.onPaymentEvent(paymentId.toString(), "payment.created", payload);

    PaymentView view = store.findById(paymentId).orElseThrow();
    assertThat(view.merchantId()).isEqualTo(merchantId);
    assertThat(view.amountMinor()).isEqualTo(21400);
    assertThat(view.currency()).isEqualTo("USD");
    assertThat(view.status()).isEqualTo("CREATED");
    assertThat(view.providerRef()).isNull();
  }

  @Test
  void paymentCapturedEventUpdatesStatusAndProviderRefWithoutLosingOtherFields() {
    UUID paymentId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    store.save(new PaymentView(paymentId, merchantId, 21400, "USD", "CREATED", null, null));

    consumer.onPaymentEvent(paymentId.toString(), "payment.captured", "{\"providerRef\":\"mock-ch-abc123\"}");

    PaymentView view = store.findById(paymentId).orElseThrow();
    assertThat(view.status()).isEqualTo("CAPTURED");
    assertThat(view.providerRef()).isEqualTo("mock-ch-abc123");
    assertThat(view.merchantId()).isEqualTo(merchantId);
    assertThat(view.amountMinor()).isEqualTo(21400);
  }

  @Test
  void paymentFailedEventUpdatesStatusAndDeclineReason() {
    UUID paymentId = UUID.randomUUID();
    store.save(new PaymentView(paymentId, UUID.randomUUID(), 21400, "USD", "CREATED", null, null));

    consumer.onPaymentEvent(
        paymentId.toString(), "payment.failed", "{\"declineReason\":\"Insufficient funds\"}");

    PaymentView view = store.findById(paymentId).orElseThrow();
    assertThat(view.status()).isEqualTo("FAILED");
    assertThat(view.declineReason()).isEqualTo("Insufficient funds");
  }

  @Test
  void paymentRefundedEventUpdatesStatusWithoutRequiringAnyPayloadFields() {
    UUID paymentId = UUID.randomUUID();
    store.save(
        new PaymentView(paymentId, UUID.randomUUID(), 21400, "USD", "CAPTURED", "mock-ch-abc123", null));

    consumer.onPaymentEvent(paymentId.toString(), "payment.refunded", "{}");

    PaymentView view = store.findById(paymentId).orElseThrow();
    assertThat(view.status()).isEqualTo("REFUNDED");
    assertThat(view.providerRef()).isEqualTo("mock-ch-abc123");
  }

  @Test
  void anEventForAPaymentNeverSeenBeforeIsIgnoredRatherThanCrashing() {
    UUID paymentId = UUID.randomUUID();

    consumer.onPaymentEvent(paymentId.toString(), "payment.captured", "{\"providerRef\":\"mock-ch-abc123\"}");

    assertThat(store.findById(paymentId)).isEmpty();
  }

  @Test
  void anUnknownEventTypeIsIgnored() {
    UUID paymentId = UUID.randomUUID();

    consumer.onPaymentEvent(paymentId.toString(), "payment.something_else", "{}");

    assertThat(store.findById(paymentId)).isEmpty();
  }
}
