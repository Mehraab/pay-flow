package dev.payflow.orchestrator.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.payflow.common.domain.Currency;
import dev.payflow.common.domain.Money;
import dev.payflow.orchestrator.activity.PaymentActivities;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
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

  @Test
  void executesAuthorizeAndCaptureThenPostToLedgerThenDispatchWebhookInOrderForAnApprovedCard() {
    UUID paymentId = UUID.randomUUID();
    UUID merchantId = UUID.randomUUID();
    Money amount = Money.of(21_400, Currency.USD);
    String approvedCard = "4242424242424242";
    PaymentWorkflowInput input = new PaymentWorkflowInput(paymentId, merchantId, amount, approvedCard);

    PaymentWorkflow workflow =
        workflowClient.newWorkflowStub(
            PaymentWorkflow.class, WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());
    workflow.execute(input);

    assertThat(activities.calls)
        .containsExactly("authorizeAndCapture", "postToLedger", "dispatchWebhook");
    assertThat(activities.authorizeAndCaptureArgs).containsExactly(paymentId, amount, approvedCard);
    assertThat(activities.postToLedgerArgs).containsExactly(paymentId, merchantId, amount);
    assertThat(activities.dispatchWebhookMerchantId).isEqualTo(merchantId);
    assertThat(activities.dispatchWebhookEventType).isEqualTo("payment.captured");
    assertThat(activities.dispatchWebhookPayload).contains(paymentId.toString()).contains("CAPTURED");
  }

  private static class RecordingPaymentActivities implements PaymentActivities {

    final List<String> calls = new ArrayList<>();
    List<Object> authorizeAndCaptureArgs;
    List<Object> postToLedgerArgs;
    UUID dispatchWebhookMerchantId;
    String dispatchWebhookEventType;
    String dispatchWebhookPayload;

    @Override
    public String authorizeAndCapture(UUID paymentId, Money amount, String cardNumber) {
      calls.add("authorizeAndCapture");
      authorizeAndCaptureArgs = List.of(paymentId, amount, cardNumber);
      return "mock-ch-abc123";
    }

    @Override
    public void postToLedger(UUID paymentId, UUID merchantId, Money amount) {
      calls.add("postToLedger");
      postToLedgerArgs = List.of(paymentId, merchantId, amount);
    }

    @Override
    public void dispatchWebhook(UUID merchantId, String eventType, String payload) {
      calls.add("dispatchWebhook");
      dispatchWebhookMerchantId = merchantId;
      dispatchWebhookEventType = eventType;
      dispatchWebhookPayload = payload;
    }

    @Override
    public void reverseProviderCharge(String providerRef) {
      calls.add("reverseProviderCharge");
    }

    @Override
    public void markPaymentFailed(UUID paymentId, String declineReason) {
      calls.add("markPaymentFailed");
    }
  }
}
