package dev.payflow.paymentquery.consumer;

import dev.payflow.paymentquery.view.PaymentMethodView;
import dev.payflow.paymentquery.view.PaymentView;
import dev.payflow.paymentquery.view.PaymentViewStore;
import java.time.Instant;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Projects {@code payment.events} onto the in-memory read model. The event type travels as the
 * {@code event-type} Kafka header (see {@code OutboxPoller}'s Javadoc in payment-command-service
 * for why it's not in the JSON body), and the payment id is the Kafka message key — set by the
 * same outbox poller — rather than a field inside every payload.
 */
@Component
public class PaymentEventConsumer {

  private static final String MOCK_PROVIDER_PREFIX = "mock-";
  private static final String MOCK_PROVIDER_DISPLAY_NAME = "Mock Simulator";
  private static final String STANDARD_PROVIDER_DISPLAY_NAME = "Standard Provider";

  private final PaymentViewStore store;
  private final ObjectMapper objectMapper;

  public PaymentEventConsumer(PaymentViewStore store, ObjectMapper objectMapper) {
    this.store = store;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(topics = "payment.events", groupId = "payment-query-service")
  public void onPaymentEvent(
      @Header(KafkaHeaders.RECEIVED_KEY) String paymentIdKey,
      @Header("event-type") String eventType,
      @Payload String payload) {
    UUID paymentId = UUID.fromString(paymentIdKey);
    switch (eventType) {
      case "payment.created" -> handleCreated(paymentId, payload);
      case "payment.authorized" -> handleProviderUpdate(paymentId, "AUTHORIZED", payload);
      case "payment.captured" -> handleProviderUpdate(paymentId, "CAPTURED", payload);
      case "payment.failed" -> handleFailed(paymentId, payload);
      case "payment.refunded" -> store.updateStatus(paymentId, "REFUNDED", null, null);
      default -> {
        // Unknown/future event type — ignored rather than failing the consumer.
      }
    }
  }

  private void handleCreated(UUID paymentId, String payload) {
    PaymentCreatedPayload created = objectMapper.readValue(payload, PaymentCreatedPayload.class);
    Instant createdAt =
        created.metadata() != null && created.metadata().occurredOn() != null
            ? created.metadata().occurredOn()
            : Instant.now();
    String customer = created.customer() != null ? created.customer() : "Customer";
    PaymentMethodView paymentMethod =
        created.paymentMethod() != null ? created.paymentMethod() : new PaymentMethodView("Visa", "4242");
    store.save(
        new PaymentView(
            paymentId,
            created.merchantId(),
            created.amount().amountMinor(),
            created.amount().currency().name(),
            "CREATED",
            null,
            null,
            createdAt,
            customer,
            paymentMethod,
            created.idempotencyKey(),
            null));
  }

  private void handleProviderUpdate(UUID paymentId, String status, String payload) {
    PaymentCapturedPayload update = objectMapper.readValue(payload, PaymentCapturedPayload.class);
    store.updateStatus(paymentId, status, update.providerRef(), providerDisplayNameFor(update.providerRef()));
  }

  private void handleFailed(UUID paymentId, String payload) {
    PaymentFailedPayload failed = objectMapper.readValue(payload, PaymentFailedPayload.class);
    store.updateFailed(paymentId, failed.declineReason());
  }

  private String providerDisplayNameFor(String providerRef) {
    if (providerRef == null) {
      return null;
    }
    return providerRef.startsWith(MOCK_PROVIDER_PREFIX) ? MOCK_PROVIDER_DISPLAY_NAME : STANDARD_PROVIDER_DISPLAY_NAME;
  }
}
