package dev.payflow.providergateway;

import dev.payflow.common.dto.AuthorizationStatus;
import dev.payflow.common.dto.AuthorizePaymentRequest;
import dev.payflow.common.dto.AuthorizePaymentResponse;
import dev.payflow.common.dto.VoidChargeResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Deterministic mock payment processor — the anti-corruption boundary between PayFlow's own
 * {@link AuthorizePaymentRequest}/{@link AuthorizePaymentResponse} contract and whatever a real
 * provider's API would look like. Routes purely on the test card's last 4 digits
 * (ARCHITECTURE_AND_PLAN.md §1.2).
 */
@Service
public class MockProviderGateway {

  private static final String APPROVED_SUFFIX = "4242";
  private static final String INSUFFICIENT_FUNDS_SUFFIX = "0002";
  private static final String EXPIRED_CARD_SUFFIX = "0069";
  private static final String PROCESSOR_TIMEOUT_SUFFIX = "0119";

  private static final int SIMULATED_NETWORK_DELAY_MILLIS = 150;
  private static final int SIMULATED_TIMEOUT_DELAY_MILLIS = 5000;

  public AuthorizePaymentResponse charge(AuthorizePaymentRequest request) {
    String cardNumber = request.cardNumber();

    if (cardNumber != null && cardNumber.endsWith(PROCESSOR_TIMEOUT_SUFFIX)) {
      simulateTimeout();
    }

    simulateNetworkDelay();

    if (cardNumber != null && cardNumber.endsWith(APPROVED_SUFFIX)) {
      return new AuthorizePaymentResponse(
          AuthorizationStatus.APPROVED, "mock-ch-" + UUID.randomUUID(), null);
    }
    if (cardNumber != null && cardNumber.endsWith(INSUFFICIENT_FUNDS_SUFFIX)) {
      return new AuthorizePaymentResponse(AuthorizationStatus.DECLINED, null, "Insufficient funds");
    }
    if (cardNumber != null && cardNumber.endsWith(EXPIRED_CARD_SUFFIX)) {
      return new AuthorizePaymentResponse(AuthorizationStatus.DECLINED, null, "Expired card");
    }
    // Every other card number, including the 0127 suffix, declines generically.
    return new AuthorizePaymentResponse(AuthorizationStatus.DECLINED, null, "Generic decline");
  }

  public VoidChargeResponse voidCharge(String providerRef) {
    simulateNetworkDelay();
    return new VoidChargeResponse("VOIDED", providerRef);
  }

  /**
   * Simulates a processor that never responds in time — the caller (the Temporal activity, or
   * whatever's calling this synchronously) is expected to time out and treat this as a failure,
   * not receive a well-formed decline. Throwing here surfaces as an HTTP 500 from {@code
   * ProviderGatewayController}, distinct from the HTTP 200/DECLINED responses the other branches
   * return.
   */
  private void simulateTimeout() {
    try {
      Thread.sleep(SIMULATED_TIMEOUT_DELAY_MILLIS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while simulating a processor timeout", e);
    }
    throw new ProviderTimeoutException("Processor timed out");
  }

  private void simulateNetworkDelay() {
    try {
      Thread.sleep(SIMULATED_NETWORK_DELAY_MILLIS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while simulating provider latency", e);
    }
  }
}
