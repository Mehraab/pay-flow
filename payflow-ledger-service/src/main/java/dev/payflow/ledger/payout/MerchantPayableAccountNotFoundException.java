package dev.payflow.ledger.payout;

/**
 * Thrown by {@code LedgerPostingService.createPayout} when the merchant has no {@code Merchant
 * Payable} account at all (never captured a payment) — a 404 via {@code LedgerExceptionHandler},
 * not a 500: the merchant simply has no payout-eligible record, which is a normal, predictable
 * outcome for a brand-new merchant, not a server fault.
 */
public class MerchantPayableAccountNotFoundException extends IllegalStateException {

  public MerchantPayableAccountNotFoundException(String message) {
    super(message);
  }
}
