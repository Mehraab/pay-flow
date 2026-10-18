package dev.payflow.providergateway;

import dev.payflow.common.dto.AuthorizationStatus;
import dev.payflow.common.dto.AuthorizePaymentRequest;
import dev.payflow.common.dto.AuthorizePaymentResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Deterministic mock payment processor — the anti-corruption boundary between PayFlow's own
 * {@link AuthorizePaymentRequest}/{@link AuthorizePaymentResponse} contract and whatever a real
 * provider's API would look like. Routes purely on the test card's last 4 digits
 * (ARCHITECTURE_AND_PLAN.md §1.2); only the {@code 4242} branch exists this chunk — the remaining
 * magic numbers (declined/timeout variants) land in Milestone 2's failure-injection work.
 */
@Service
public class MockProviderGateway {

  private static final String APPROVED_CARD_SUFFIX = "4242";
  private static final int SIMULATED_NETWORK_DELAY_MILLIS = 150;

  public AuthorizePaymentResponse charge(AuthorizePaymentRequest request) {
    simulateNetworkDelay();

    if (request.cardNumber() != null && request.cardNumber().endsWith(APPROVED_CARD_SUFFIX)) {
      return new AuthorizePaymentResponse(
          AuthorizationStatus.APPROVED, "mock-ch-" + UUID.randomUUID(), null);
    }
    return new AuthorizePaymentResponse(
        AuthorizationStatus.DECLINED, null, "Card not supported in Milestone 1");
  }

  /**
   * Milestone 1 stub: always succeeds, no state to reverse yet. Real void/reversal semantics
   * arrive with the {@code reverseProviderCharge} compensation activity in Milestone 2.
   */
  public void voidCharge(String providerRef) {
    simulateNetworkDelay();
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
