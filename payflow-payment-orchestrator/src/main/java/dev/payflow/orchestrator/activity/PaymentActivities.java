package dev.payflow.orchestrator.activity;

import dev.payflow.common.domain.Money;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import java.util.UUID;

@ActivityInterface
public interface PaymentActivities {

  @ActivityMethod
  String authorizeAndCapture(UUID paymentId, Money amount, String cardNumber);

  @ActivityMethod
  void postToLedger(UUID paymentId, UUID merchantId, Money amount);

  @ActivityMethod
  void dispatchWebhook(UUID merchantId, String eventType, String payload);

  /** Compensation activity — not yet called by {@code PaymentWorkflow}'s Milestone 1 golden path. */
  @ActivityMethod
  void reverseProviderCharge(String providerRef);

  /** Compensation activity — not yet called by {@code PaymentWorkflow}'s Milestone 1 golden path. */
  @ActivityMethod
  void markPaymentFailed(UUID paymentId, String declineReason);
}
