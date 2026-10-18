package dev.payflow.paymentcommand.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.payflow.common.domain.Currency;
import dev.payflow.common.domain.Money;
import dev.payflow.common.domain.PaymentStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentTest {

  private final UUID merchantId = UUID.randomUUID();
  private final Money amount = Money.of(21400, Currency.USD);

  @Test
  void createRaisesPaymentCreatedAndSetsInitialState() {
    var payment = Payment.create(PaymentId.generate(), merchantId, amount, "demo-001");

    assertThat(payment.status()).isEqualTo(PaymentStatus.CREATED);
    assertThat(payment.merchantId()).isEqualTo(merchantId);
    assertThat(payment.amount()).isEqualTo(amount);
    assertThat(payment.idempotencyKey()).isEqualTo("demo-001");
    assertThat(payment.version()).isZero();

    var events = payment.pullUncommittedEvents();
    assertThat(events).hasSize(1);
    assertThat(events.get(0)).isInstanceOf(PaymentCreated.class);
  }

  @Test
  void authorizeFromCreatedSucceeds() {
    var payment = Payment.create(PaymentId.generate(), merchantId, amount, "demo-002");
    payment.pullUncommittedEvents();

    payment.authorize("mock-ch-abc123");

    assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(payment.providerRef()).isEqualTo("mock-ch-abc123");
    assertThat(payment.pullUncommittedEvents()).hasSize(1).first().isInstanceOf(PaymentAuthorized.class);
  }

  @Test
  void authorizeFromAnyOtherStatusIsRejected() {
    var payment = Payment.create(PaymentId.generate(), merchantId, amount, "demo-003");
    payment.capture("mock-ch-abc123");

    assertThatThrownBy(() -> payment.authorize("mock-ch-xyz")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void captureFromCreatedSucceeds() {
    var payment = Payment.create(PaymentId.generate(), merchantId, amount, "demo-004");

    payment.capture("mock-ch-abc123");

    assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
    assertThat(payment.providerRef()).isEqualTo("mock-ch-abc123");
  }

  @Test
  void captureFromAuthorizedSucceeds() {
    var payment = Payment.create(PaymentId.generate(), merchantId, amount, "demo-005");
    payment.authorize("mock-ch-abc123");

    payment.capture("mock-ch-abc123");

    assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
  }

  @Test
  void captureAfterFailedIsRejected() {
    var payment = Payment.create(PaymentId.generate(), merchantId, amount, "demo-006");
    payment.fail("Insufficient funds");

    assertThatThrownBy(() -> payment.capture("mock-ch-abc123")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void failFromCreatedSucceeds() {
    var payment = Payment.create(PaymentId.generate(), merchantId, amount, "demo-007");

    payment.fail("Insufficient funds");

    assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payment.declineReason()).isEqualTo("Insufficient funds");
  }

  @Test
  void failAfterCapturedIsRejected() {
    var payment = Payment.create(PaymentId.generate(), merchantId, amount, "demo-008");
    payment.capture("mock-ch-abc123");

    assertThatThrownBy(() -> payment.fail("too late")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void failAfterAuthorizedSucceeds() {
    var payment = Payment.create(PaymentId.generate(), merchantId, amount, "demo-009");
    payment.authorize("mock-ch-abc123");

    payment.fail("provider timeout during capture");

    assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
  }

  @Test
  void reconstituteRestoresStateWithoutRaisingEvents() {
    var id = PaymentId.generate();

    var payment =
        Payment.reconstitute(
            id, 3, merchantId, amount, PaymentStatus.CAPTURED, "demo-010", "mock-ch-abc123", null);

    assertThat(payment.id()).isEqualTo(id);
    assertThat(payment.version()).isEqualTo(3);
    assertThat(payment.status()).isEqualTo(PaymentStatus.CAPTURED);
    assertThat(payment.providerRef()).isEqualTo("mock-ch-abc123");
    assertThat(payment.pullUncommittedEvents()).isEmpty();
  }
}
