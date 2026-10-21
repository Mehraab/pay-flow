package dev.payflow.orchestrator.activity;

import dev.payflow.common.domain.Money;
import dev.payflow.common.dto.AuthorizationStatus;
import dev.payflow.common.dto.AuthorizePaymentRequest;
import dev.payflow.common.dto.AuthorizePaymentResponse;
import dev.payflow.common.dto.VoidChargeRequest;
import io.temporal.failure.ApplicationFailure;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/**
 * Calls the four downstream services synchronously over HTTP — Temporal's own retry policy
 * (configured on the Activity options in {@code PaymentWorkflowImpl}) covers transient failures,
 * so no retry logic lives here.
 */
@Component
public class PaymentActivitiesImpl implements PaymentActivities {

  private final RestClient restClient;
  private final String providerGatewayUrl;
  private final String paymentCommandUrl;
  private final String ledgerUrl;
  private final String notificationUrl;

  public PaymentActivitiesImpl(
      RestClient restClient,
      @Value("${payflow.orchestrator.provider-gateway-url}") String providerGatewayUrl,
      @Value("${payflow.orchestrator.payment-command-url}") String paymentCommandUrl,
      @Value("${payflow.orchestrator.ledger-url}") String ledgerUrl,
      @Value("${payflow.orchestrator.notification-url}") String notificationUrl) {
    this.restClient = restClient;
    this.providerGatewayUrl = providerGatewayUrl;
    this.paymentCommandUrl = paymentCommandUrl;
    this.ledgerUrl = ledgerUrl;
    this.notificationUrl = notificationUrl;
  }

  @Override
  public String authorizeAndCapture(UUID paymentId, Money amount, String cardNumber) {
    AuthorizePaymentResponse authorization;
    try {
      authorization =
          restClient
              .post()
              .uri(providerGatewayUrl + "/internal/providers/charge")
              .contentType(MediaType.APPLICATION_JSON)
              .body(new AuthorizePaymentRequest(paymentId, amount, cardNumber))
              .retrieve()
              .body(AuthorizePaymentResponse.class);
    } catch (HttpServerErrorException e) {
      // The mock provider's 0119-suffix branch throws after a simulated 5s delay, surfacing
      // here as a 504 — treated as a decline, not retried (a real processor that just timed
      // out is not reliably safe to retry blind).
      throw ApplicationFailure.newNonRetryableFailure(
          "Processor timed out", "ProcessorTimeout", "Processor timed out");
    }

    if (authorization == null || authorization.status() != AuthorizationStatus.APPROVED) {
      String reason = authorization == null ? "No response from provider gateway" : authorization.declineReason();
      throw ApplicationFailure.newNonRetryableFailure(
          "Payment declined: " + reason, "PaymentDeclined", reason);
    }

    restClient
        .post()
        .uri(paymentCommandUrl + "/internal/payments/{id}/capture", paymentId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new CapturePaymentRequest(authorization.providerRef()))
        .retrieve()
        .toBodilessEntity();

    return authorization.providerRef();
  }

  @Override
  public void postToLedger(UUID paymentId, UUID merchantId, Money amount, boolean forceLedgerFailure) {
    var request =
        restClient
            .post()
            .uri(ledgerUrl + "/internal/ledger/post-capture")
            .contentType(MediaType.APPLICATION_JSON);
    if (forceLedgerFailure) {
      request = request.header("X-Force-Ledger-Failure", "true");
    }
    request
        .body(
            new PostLedgerCaptureRequest(
                paymentId, merchantId, amount.amountMinor(), amount.currency().name()))
        .retrieve()
        .toBodilessEntity();
  }

  @Override
  public void dispatchWebhook(UUID merchantId, String eventType, String payload) {
    restClient
        .post()
        .uri(notificationUrl + "/internal/webhooks/dispatch")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new DispatchWebhookRequest(merchantId, eventType, payload))
        .retrieve()
        .toBodilessEntity();
  }

  @Override
  public void reverseProviderCharge(String providerRef) {
    restClient
        .post()
        .uri(providerGatewayUrl + "/internal/providers/void")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new VoidChargeRequest(providerRef))
        .retrieve()
        .toBodilessEntity();
  }

  @Override
  public void markPaymentFailed(UUID paymentId, String declineReason) {
    restClient
        .post()
        .uri(paymentCommandUrl + "/internal/payments/{id}/fail", paymentId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new FailPaymentRequest(declineReason))
        .retrieve()
        .toBodilessEntity();
  }

  @Override
  public void refundPayment(UUID paymentId) {
    restClient
        .post()
        .uri(paymentCommandUrl + "/internal/payments/{id}/refund", paymentId)
        .retrieve()
        .toBodilessEntity();
  }
}
