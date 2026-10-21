package dev.payflow.ledger.consumer;

import dev.payflow.ledger.posting.LedgerPostingService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Posts the reversal for a {@code payment.refunded} event — the ONLY Kafka consumer in this
 * service (capture posting is synchronous, called directly by the Temporal {@code postToLedger}
 * Activity; see ARCHITECTURE_AND_PLAN.md §1.2 and §3's Milestone 2 task list).
 *
 * <p>Note what the payload does NOT carry: {@code PaymentRefunded} is {@code record
 * PaymentRefunded(EventMetadata metadata)} — no merchantId, no amount. The event id used for
 * {@code processed_events} dedup comes from {@code metadata.eventId} inside the payload; the
 * payment id comes from the Kafka message key (set by the outbox poller), not the payload.
 * {@code LedgerPostingService.reverseCaptureIfExists} recovers the merchant and amount by
 * reading back the original capture's own {@code ledger_entries} rows.
 */
@Component
public class PaymentRefundedConsumer {

  private static final Logger log = LoggerFactory.getLogger(PaymentRefundedConsumer.class);
  private static final String CONSUMER_NAME = "ledger-service";

  private final ProcessedEventRepository processedEventRepository;
  private final LedgerPostingService postingService;
  private final ObjectMapper objectMapper;

  public PaymentRefundedConsumer(
      ProcessedEventRepository processedEventRepository,
      LedgerPostingService postingService,
      ObjectMapper objectMapper) {
    this.processedEventRepository = processedEventRepository;
    this.postingService = postingService;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(topics = "payment.events", groupId = CONSUMER_NAME)
  public void onPaymentEvent(
      @Header(KafkaHeaders.RECEIVED_KEY) String paymentIdKey,
      @Header("event-type") String eventType,
      @Payload String payload) {
    if (!"payment.refunded".equals(eventType)) {
      return;
    }

    UUID paymentId = UUID.fromString(paymentIdKey);
    UUID eventId = objectMapper.readValue(payload, PaymentRefundedPayload.class).metadata().eventId();

    if (!processedEventRepository.markProcessed(eventId, CONSUMER_NAME)) {
      log.info("Skipping already-processed payment.refunded event {} for payment {}", eventId, paymentId);
      return;
    }

    postingService.reverseCaptureIfExists(paymentId);
  }
}
