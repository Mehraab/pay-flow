package dev.payflow.paymentcommand.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.payflow.common.domain.Currency;
import dev.payflow.common.domain.Money;
import dev.payflow.common.domain.PaymentStatus;
import dev.payflow.cqrs.persistence.AggregateRepository;
import dev.payflow.paymentcommand.domain.Payment;
import dev.payflow.paymentcommand.domain.PaymentDomainEvent;
import dev.payflow.paymentcommand.domain.PaymentId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefundPaymentHandlerTest {

  @SuppressWarnings("unchecked")
  private final AggregateRepository<Payment, PaymentId, PaymentDomainEvent> repository =
      mock(AggregateRepository.class);

  private final RefundPaymentHandler handler = new RefundPaymentHandler(repository);

  @Test
  void refundsACapturedPaymentAndSavesIt() {
    PaymentId id = PaymentId.generate();
    Payment payment =
        Payment.create(id, UUID.randomUUID(), Money.of(21_400, Currency.USD), "demo-001");
    payment.capture("mock-ch-abc123");
    when(repository.getById(id)).thenReturn(payment);

    handler.handle(new RefundPaymentCommand(id));

    assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
    verify(repository).save(payment);
  }

  @Test
  void refundingAPaymentThatIsNotCapturedPropagatesTheIllegalStateException() {
    PaymentId id = PaymentId.generate();
    Payment payment =
        Payment.create(id, UUID.randomUUID(), Money.of(21_400, Currency.USD), "demo-002");
    when(repository.getById(id)).thenReturn(payment);

    assertThatThrownBy(() -> handler.handle(new RefundPaymentCommand(id)))
        .isInstanceOf(IllegalStateException.class);
  }
}
