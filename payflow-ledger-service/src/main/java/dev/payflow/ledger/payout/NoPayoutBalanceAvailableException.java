package dev.payflow.ledger.payout;

/**
 * Thrown by {@code LedgerPostingService.createPayout} when the merchant's {@code Merchant
 * Payable} balance is zero or negative — a 409 via {@code LedgerExceptionHandler}, not a 500:
 * a real, client-triggerable state (the merchant simply has nothing to pay out right now), not
 * a server fault.
 */
public class NoPayoutBalanceAvailableException extends IllegalStateException {

  public NoPayoutBalanceAvailableException(String message) {
    super(message);
  }
}
