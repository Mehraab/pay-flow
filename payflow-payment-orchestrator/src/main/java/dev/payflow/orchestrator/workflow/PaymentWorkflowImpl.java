package dev.payflow.orchestrator.workflow;

import dev.payflow.orchestrator.activity.PaymentActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.Map;

/**
 * Milestone 2 saga (ARCHITECTURE_AND_PLAN.md §1.2, BR-5):
 *
 * <ul>
 *   <li>{@code authorizeAndCapture} fails → mark the payment {@code FAILED}, dispatch {@code
 *       payment.failed}, stop. No compensation needed — nothing was ever captured or posted.
 *   <li>{@code postToLedger} fails (after its own retries exhaust) → void the provider charge,
 *       refund the payment ({@code REFUNDED}), dispatch {@code payment.refunded}, stop. The
 *       ledger itself never has a half-posted entry to clean up: {@code post-capture} is one DB
 *       transaction on the Ledger Service side, so a rejected call wrote nothing at all.
 *   <li>Both succeed → dispatch {@code payment.captured}, the Milestone 1 golden path.
 * </ul>
 */
public class PaymentWorkflowImpl implements PaymentWorkflow {

  private final PaymentActivities activities;

  public PaymentWorkflowImpl() {
    ActivityOptions defaultOptions =
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
            .build();

    // Declines and processor timeouts are already non-retryable at the source (see
    // PaymentActivitiesImpl), but capped at 1 attempt here too so a genuine transport error
    // talking to the provider gateway doesn't retry blind against a one-shot charge attempt.
    ActivityOptions authorizeAndCaptureOptions =
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
            .build();

    // Short, finite retry so a ledger outage surfaces to the saga quickly instead of the
    // workflow's default 3-attempt backoff masking it for several seconds.
    ActivityOptions postToLedgerOptions =
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .setRetryOptions(
                RetryOptions.newBuilder()
                    .setMaximumAttempts(2)
                    .setInitialInterval(Duration.ofMillis(200))
                    .setBackoffCoefficient(1.0)
                    .build())
            .build();

    // Per-method ActivityOptions are keyed by the activity TYPE name, not the Java method
    // name — Temporal's default naming capitalizes the method name's first letter (e.g.
    // "postToLedger" -> "PostToLedger"), since @ActivityMethod has no explicit name= here.
    this.activities =
        Workflow.newActivityStub(
            PaymentActivities.class,
            defaultOptions,
            Map.of(
                "AuthorizeAndCapture", authorizeAndCaptureOptions,
                "PostToLedger", postToLedgerOptions));
  }

  @Override
  public void execute(PaymentWorkflowInput input) {
    String providerRef;
    try {
      providerRef = activities.authorizeAndCapture(input.paymentId(), input.amount(), input.cardNumber());
    } catch (ActivityFailure e) {
      activities.markPaymentFailed(input.paymentId(), extractReason(e));
      activities.dispatchWebhook(
          input.merchantId(),
          "payment.failed",
          "{\"paymentId\":\"" + input.paymentId() + "\",\"status\":\"FAILED\"}");
      return;
    }

    try {
      activities.postToLedger(input.paymentId(), input.merchantId(), input.amount(), input.forceLedgerFailure());
    } catch (ActivityFailure e) {
      activities.reverseProviderCharge(providerRef);
      activities.refundPayment(input.paymentId());
      activities.dispatchWebhook(
          input.merchantId(),
          "payment.refunded",
          "{\"paymentId\":\"" + input.paymentId() + "\",\"status\":\"REFUNDED\"}");
      return;
    }

    activities.dispatchWebhook(
        input.merchantId(),
        "payment.captured",
        "{\"paymentId\":\"" + input.paymentId() + "\",\"status\":\"CAPTURED\"}");
  }

  /**
   * {@code authorizeAndCapture}'s failures always carry the human-readable decline reason as the
   * {@link ApplicationFailure}'s first detail value (not just its formatted message, whose exact
   * shape isn't part of the public contract) — see {@code PaymentActivitiesImpl}.
   */
  private String extractReason(ActivityFailure failure) {
    if (failure.getCause() instanceof ApplicationFailure applicationFailure) {
      var details = applicationFailure.getDetails();
      if (details != null && details.getSize() > 0) {
        return details.get(0, String.class);
      }
      return applicationFailure.getMessage();
    }
    return failure.getMessage();
  }
}
