package dev.payflow.common.event;

import static org.assertj.core.api.Assertions.assertThat;

import dev.payflow.common.domain.Currency;
import dev.payflow.common.domain.Money;
import dev.payflow.cqrs.domain.EventMetadata;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentEventTest {

  @Test
  void paymentCreatedEventCarriesItsMetadataAndPayload() {
    var paymentId = UUID.randomUUID();
    var merchantId = UUID.randomUUID();
    var correlationId = UUID.randomUUID();
    var metadata = EventMetadata.next(paymentId, correlationId, null);
    var amount = Money.of(21400, Currency.USD);

    var event = new PaymentCreatedEvent(metadata, paymentId, merchantId, amount, "demo-001");

    assertThat(event.aggregateId()).isEqualTo(paymentId);
    assertThat(event.correlationId()).isEqualTo(correlationId);
    assertThat(event.causationId()).isNull();
    assertThat(event.merchantId()).isEqualTo(merchantId);
    assertThat(event.amount()).isEqualTo(amount);
    assertThat(event.idempotencyKey()).isEqualTo("demo-001");
  }

  @Test
  void paymentCapturedEventCarriesItsProviderReference() {
    var paymentId = UUID.randomUUID();
    var metadata = EventMetadata.next(paymentId, UUID.randomUUID(), null);

    var event = new PaymentCapturedEvent(metadata, paymentId, "mock-ch-abc123");

    assertThat(event.aggregateId()).isEqualTo(paymentId);
    assertThat(event.providerRef()).isEqualTo("mock-ch-abc123");
  }

  @Test
  void paymentFailedEventCarriesItsDeclineReason() {
    var paymentId = UUID.randomUUID();
    var metadata = EventMetadata.next(paymentId, UUID.randomUUID(), null);

    var event = new PaymentFailedEvent(metadata, paymentId, "Insufficient funds");

    assertThat(event.aggregateId()).isEqualTo(paymentId);
    assertThat(event.declineReason()).isEqualTo("Insufficient funds");
  }

  @Test
  void paymentRefundedEventCarriesItsPaymentId() {
    var paymentId = UUID.randomUUID();
    var metadata = EventMetadata.next(paymentId, UUID.randomUUID(), null);

    var event = new PaymentRefundedEvent(metadata, paymentId);

    assertThat(event.aggregateId()).isEqualTo(paymentId);
    assertThat(event.paymentId()).isEqualTo(paymentId);
  }

  @Test
  void sealedPaymentEventSwitchIsExhaustive() {
    PaymentEvent event =
        new PaymentFailedEvent(
            EventMetadata.next(UUID.randomUUID(), UUID.randomUUID(), null),
            UUID.randomUUID(),
            "generic decline");

    String description =
        switch (event) {
          case PaymentCreatedEvent e -> "created";
          case PaymentCapturedEvent e -> "captured";
          case PaymentFailedEvent e -> "failed";
          case PaymentRefundedEvent e -> "refunded";
        };

    assertThat(description).isEqualTo("failed");
  }
}
