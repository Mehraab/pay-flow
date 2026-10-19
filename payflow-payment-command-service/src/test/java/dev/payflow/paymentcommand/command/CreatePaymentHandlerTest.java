package dev.payflow.paymentcommand.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import dev.payflow.common.domain.Currency;
import dev.payflow.common.domain.Money;
import dev.payflow.common.domain.PaymentStatus;
import dev.payflow.orchestrator.workflow.PaymentWorkflowInput;
import dev.payflow.paymentcommand.AbstractPostgresIntegrationTest;
import dev.payflow.paymentcommand.domain.Payment;
import dev.payflow.paymentcommand.persistence.PostgresPaymentRepository;
import dev.payflow.paymentcommand.workflow.PaymentWorkflowStarter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Verifies the new Milestone 1 Chunk 4 behavior end to end against a real Postgres (not a mock
 * repository), while substituting {@link PaymentWorkflowStarter} — Temporal's own client API
 * isn't something Mockito can stand in for, so the seam is this adapter, not {@code
 * WorkflowClient} itself (see that class's Javadoc).
 */
@SpringBootTest
class CreatePaymentHandlerTest extends AbstractPostgresIntegrationTest {

  @Autowired private CreatePaymentHandler handler;
  @Autowired private PostgresPaymentRepository repository;
  @MockitoBean private PaymentWorkflowStarter workflowStarter;

  @Test
  void savesThePaymentThenStartsThePaymentWorkflowWithTheGeneratedId() {
    UUID merchantId = UUID.randomUUID();
    Money amount = Money.of(21_400, Currency.USD);
    String cardNumber = "4242424242424242";
    String idempotencyKey = "test-" + UUID.randomUUID();

    var paymentId = handler.handle(new CreatePaymentCommand(merchantId, amount, cardNumber, idempotencyKey));

    Payment saved = repository.findById(paymentId).orElseThrow();
    assertThat(saved.status()).isEqualTo(PaymentStatus.CREATED);
    assertThat(saved.merchantId()).isEqualTo(merchantId);
    assertThat(saved.amount()).isEqualTo(amount);

    verify(workflowStarter)
        .start(eq(new PaymentWorkflowInput(paymentId.value(), merchantId, amount, cardNumber)));
  }
}
