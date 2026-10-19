package dev.payflow.orchestrator.workflow;

import dev.payflow.orchestrator.activity.PaymentActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/**
 * Milestone 1 golden path: {@code authorizeAndCapture → postToLedger → dispatchWebhook},
 * sequential, no compensation yet (ARCHITECTURE_AND_PLAN.md §1.2 — sagas and the
 * {@code reverseProviderCharge}/{@code markPaymentFailed} branches land in Milestone 2).
 */
public class PaymentWorkflowImpl implements PaymentWorkflow {

  private final PaymentActivities activities;

  public PaymentWorkflowImpl() {
    ActivityOptions options =
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
            .build();
    this.activities = Workflow.newActivityStub(PaymentActivities.class, options);
  }

  @Override
  public void execute(PaymentWorkflowInput input) {
    String providerRef =
        activities.authorizeAndCapture(input.paymentId(), input.amount(), input.cardNumber());
    activities.postToLedger(input.paymentId(), input.merchantId(), input.amount());
    activities.dispatchWebhook(
        input.merchantId(),
        "payment.captured",
        "{\"paymentId\":\"" + input.paymentId() + "\",\"status\":\"CAPTURED\"}");
  }
}
