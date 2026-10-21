package dev.payflow.orchestrator.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.payflow.common.domain.Currency;
import dev.payflow.common.domain.Money;
import dev.payflow.orchestrator.activity.PaymentActivities;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentWorkflowImplTest {

  private static final String TASK_QUEUE = "test-payment-task-queue";

  private TestWorkflowEnvironment testEnvironment;
  private WorkflowClient workflowClient;
  private RecordingPaymentActivities activities;

  @BeforeEach
  void setUp() {
    testEnvironment = TestWorkflowEnvironment.newInstance();
    Worker worker = testEnvironment.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(PaymentWorkflowImpl.class);

    // Not a Mockito mock: Temporal's activity-impl registration rejects a Mockito-generated
    // proxy class for an @ActivityInterface, because the dynamic subclass copies the
    // interface's @ActivityMethod annotations onto its own override methods, which Temporal's
    // POJOActivityImplMetadata explicitly treats as an error ("can be used only on the
    // interface method it implements"). A plain hand-written fake sidesteps that entirely.
    activities = new RecordingPaymentActivities();
    worker.registerActivitiesImplementations(activities);

    workflowClient = testEnvironment.getWorkflowClient();
    testEnvironment.start();
  }

  @AfterEach
  void tearDown() {
    testEnvironment.close();
  }

  private PaymentWorkflow newWorkflowStub() {
    return workflowClient.newWorkflowStub(
        PaymentWorkflow.class, WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());
  }

  @Test
  void executesAuthorizeAndCaptureThenPostToLedgerThenDispatchWebhookInOrderForAnApprovedCard() {
    UUID paymentId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    Money amount = Money.of(21_400, Currency.USD);
    String approvedCard = "4242424242424242";
    PaymentWorkflowInput input = new PaymentWorkflowInput(paymentId, merchantId, amount, approvedCard, false);

    newWorkflowStub().execute(input);

    assertThat(activities.calls)
        .containsExactly("authorizeAndCapture", "postToLedger", "dispatchWebhook:payment.captured");
    assertThat(activities.authorizeAndCaptureArgs).containsExactly(paymentId, amount, approvedCard);
    assertThat(activities.postToLedgerArgs).containsExactly(paymentId, merchantId, amount);
    assertThat(activities.dispatchWebhookMerchantId).isEqualTo(merchantId);
    assertThat(activities.dispatchWebhookEventType).isEqualTo("payment.captured");
    assertThat(activities.dispatchWebhookPayload).contains(paymentId.toString()).contains("CAPTURED");
  }

  @Test
  void aDeclinedCardMarksThePaymentFailedAndDispatchesAFailedWebhookWithoutTouchingTheLedger() {
    UUID paymentId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    Money amount = Money.of(21_400, Currency.USD);
    String declinedCard = "4000000000000002";
    PaymentWorkflowInput input = new PaymentWorkflowInput(paymentId, merchantId, amount, declinedCard, false);

    activities.failAuthorizeAndCaptureWith(
        ApplicationFailure.newNonRetryableFailure(
            "Payment declined: Insufficient funds", "PaymentDeclined", "Insufficient funds"));

    newWorkflowStub().execute(input);

    assertThat(activities.calls)
        .containsExactly("authorizeAndCapture", "markPaymentFailed", "dispatchWebhook:payment.failed");
    assertThat(activities.markPaymentFailedPaymentId).isEqualTo(paymentId);
    assertThat(activities.markPaymentFailedReason).isEqualTo("Insufficient funds");
    assertThat(activities.dispatchWebhookEventType).isEqualTo("payment.failed");
    assertThat(activities.dispatchWebhookPayload).contains(paymentId.toString()).contains("FAILED");
  }

  @Test
  void aLedgerFailureRunsTheSagaCompensationAndDispatchesARefundedWebhook() {
    UUID paymentId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    Money amount = Money.of(21_400, Currency.USD);
    String approvedCard = "4242424242424242";
    PaymentWorkflowInput input = new PaymentWorkflowInput(paymentId, merchantId, amount, approvedCard, true);

    // Retryable (not newNonRetryableFailure) so postToLedgerOptions' 2-attempt RetryOptions
    // actually exercises a retry before the saga's own catch block runs compensation.
    activities.failPostToLedgerWith(ApplicationFailure.newFailure("Simulated ledger failure", "LedgerFailure"));

    newWorkflowStub().execute(input);

    assertThat(activities.calls)
        .containsExactly(
            "authorizeAndCapture",
            "postToLedger",
            "postToLedger",
            "reverseProviderCharge",
            "refundPayment",
            "dispatchWebhook:payment.refunded");
    assertThat(activities.reverseProviderChargeArg).isEqualTo("mock-ch-abc123");
    assertThat(activities.refundPaymentArg).isEqualTo(paymentId);
    assertThat(activities.dispatchWebhookEventType).isEqualTo("payment.refunded");
    assertThat(activities.dispatchWebhookPayload).contains(paymentId.toString()).contains("REFUNDED");
  }

  private static class RecordingPaymentActivities implements PaymentActivities {

    final List<String> calls = new ArrayList<>();
    List<Object> authorizeAndCaptureArgs;
    List<Object> postToLedgerArgs;
    UUID dispatchWebhookMerchantId;
    String dispatchWebhookEventType;
    String dispatchWebhookPayload;
    String reverseProviderChargeArg;
    UUID markPaymentFailedPaymentId;
    String markPaymentFailedReason;
    UUID refundPaymentArg;

    private RuntimeException authorizeAndCaptureFailure;
    private RuntimeException postToLedgerFailure;

    void failAuthorizeAndCaptureWith(RuntimeException failure) {
      this.authorizeAndCaptureFailure = failure;
    }

    void failPostToLedgerWith(RuntimeException failure) {
      this.postToLedgerFailure = failure;
    }

    @Override
    public String authorizeAndCapture(UUID paymentId, Money amount, String cardNumber) {
      calls.add("authorizeAndCapture");
      authorizeAndCaptureArgs = List.of(paymentId, amount, cardNumber);
      if (authorizeAndCaptureFailure != null) {
        throw authorizeAndCaptureFailure;
      }
      return "mock-ch-abc123";
    }

    @Override
    public void postToLedger(UUID paymentId, UUID merchantId, Money amount, boolean forceLedgerFailure) {
      calls.add("postToLedger");
      postToLedgerArgs = List.of(paymentId, merchantId, amount);
      if (postToLedgerFailure != null) {
        throw postToLedgerFailure;
      }
    }

    @Override
    public void dispatchWebhook(UUID merchantId, String eventType, String payload) {
      calls.add("dispatchWebhook:" + eventType);
      dispatchWebhookMerchantId = merchantId;
      dispatchWebhookEventType = eventType;
      dispatchWebhookPayload = payload;
    }

    @Override
    public void reverseProviderCharge(String providerRef) {
      calls.add("reverseProviderCharge");
      reverseProviderChargeArg = providerRef;
    }

    @Override
    public void markPaymentFailed(UUID paymentId, String declineReason) {
      calls.add("markPaymentFailed");
      markPaymentFailedPaymentId = paymentId;
      markPaymentFailedReason = declineReason;
    }

    @Override
    public void refundPayment(UUID paymentId) {
      calls.add("refundPayment");
      refundPaymentArg = paymentId;
    }
  }
}
