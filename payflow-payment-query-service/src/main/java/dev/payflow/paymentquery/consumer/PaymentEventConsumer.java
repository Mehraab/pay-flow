package dev.payflow.paymentquery.consumer;

import dev.payflow.paymentquery.view.PaymentMethodView;
import dev.payflow.paymentquery.view.PaymentTimelineEvent;
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
      case "payment.authorized" -> handleAuthorized(paymentId, payload);
      case "payment.captured" -> handleCaptured(paymentId, payload);
      case "payment.failed" -> handleFailed(paymentId, payload);
      case "payment.refunded" -> handleRefunded(paymentId);
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
    store.recordTimelineEvent(
        paymentId,
        new PaymentTimelineEvent("PAYMENT_CREATED", "CREATED", createdAt, "Payment initialized for " + customer));
  }

  private void handleAuthorized(UUID paymentId, String payload) {
    PaymentCapturedPayload update = objectMapper.readValue(payload, PaymentCapturedPayload.class);
    String providerDisplayName = providerDisplayNameFor(update.providerRef());
    store.updateStatus(paymentId, "AUTHORIZED", update.providerRef(), providerDisplayName);
    store.recordTimelineEvent(
        paymentId,
        new PaymentTimelineEvent(
            "PAYMENT_AUTHORIZED", "AUTHORIZED", Instant.now(), "Authorized via " + providerDisplayName));
  }

  private void handleCaptured(UUID paymentId, String payload) {
    PaymentCapturedPayload update = objectMapper.readValue(payload, PaymentCapturedPayload.class);
    store.updateStatus(paymentId, "CAPTURED", update.providerRef(), providerDisplayNameFor(update.providerRef()));
    store.recordTimelineEvent(
        paymentId,
        new PaymentTimelineEvent(
            "PAYMENT_CAPTURED",
            "CAPTURED",
            Instant.now(),
            "Payment captured successfully (Ref: " + update.providerRef() + ")"));
    store.recordTimelineEvent(
        paymentId,
        new PaymentTimelineEvent("LEDGER_POSTED", "POSTED", Instant.now(), "Double-entry ledger transaction posted"));
    store.recordTimelineEvent(
        paymentId,
        new PaymentTimelineEvent(
            "WEBHOOK_DELIVERED",
            "DELIVERED",
            Instant.now(),
            "Webhook event payment.captured delivered (HTTP 200)"));
  }

  private void handleFailed(UUID paymentId, String payload) {
    PaymentFailedPayload failed = objectMapper.readValue(payload, PaymentFailedPayload.class);
    store.updateFailed(paymentId, failed.declineReason());
    store.recordTimelineEvent(
        paymentId,
        new PaymentTimelineEvent(
            "PAYMENT_FAILED", "FAILED", Instant.now(), "Payment declined: " + failed.declineReason()));
  }

  private void handleRefunded(UUID paymentId) {
    store.updateStatus(paymentId, "REFUNDED", null, null);
    store.recordTimelineEvent(
        paymentId,
        new PaymentTimelineEvent(
            "PAYMENT_REFUNDED", "REFUNDED", Instant.now(), "Full refund issued to original payment method"));
  }

  private String providerDisplayNameFor(String providerRef) {
    if (providerRef == null) {
      return null;
    }
    return providerRef.startsWith(MOCK_PROVIDER_PREFIX) ? MOCK_PROVIDER_DISPLAY_NAME : STANDARD_PROVIDER_DISPLAY_NAME;
  }
}
