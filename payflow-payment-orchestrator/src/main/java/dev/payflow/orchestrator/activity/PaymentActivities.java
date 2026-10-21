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
  void postToLedger(UUID paymentId, UUID merchantId, Money amount, boolean forceLedgerFailure);

  @ActivityMethod
  void dispatchWebhook(UUID merchantId, String eventType, String payload);

  /** Compensation activity: voids the provider charge when {@code postToLedger} fails. */
  @ActivityMethod
  void reverseProviderCharge(String providerRef);

  /** Compensation activity: marks the payment {@code FAILED} when authorization fails. */
  @ActivityMethod
  void markPaymentFailed(UUID paymentId, String declineReason);

  /** Compensation activity: marks the payment {@code REFUNDED} when {@code postToLedger} fails. */
  @ActivityMethod
  void refundPayment(UUID paymentId);
}
