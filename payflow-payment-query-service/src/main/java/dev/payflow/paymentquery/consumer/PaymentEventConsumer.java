package dev.payflow.paymentquery.consumer;

import dev.payflow.paymentquery.view.PaymentView;
import dev.payflow.paymentquery.view.PaymentViewStore;
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
      case "payment.captured" -> handleCaptured(paymentId, payload);
      case "payment.failed" -> store.updateStatus(paymentId, "FAILED", null);
      default -> {
        // Unknown/future event type — ignored rather than failing the consumer.
      }
    }
  }

  private void handleCreated(UUID paymentId, String payload) {
    PaymentCreatedPayload created = objectMapper.readValue(payload, PaymentCreatedPayload.class);
    store.save(
        new PaymentView(
            paymentId,
            created.merchantId(),
            created.amount().amountMinor(),
            created.amount().currency().name(),
            "CREATED",
            null));
  }

  private void handleCaptured(UUID paymentId, String payload) {
    PaymentCapturedPayload captured = objectMapper.readValue(payload, PaymentCapturedPayload.class);
    store.updateStatus(paymentId, "CAPTURED", captured.providerRef());
  }
}
