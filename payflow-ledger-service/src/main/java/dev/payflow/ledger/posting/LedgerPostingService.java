package dev.payflow.ledger.posting;

import dev.payflow.common.domain.Currency;
import dev.payflow.common.domain.Money;
import dev.payflow.ledger.account.AccountType;
import dev.payflow.ledger.account.LedgerAccount;
import dev.payflow.ledger.account.LedgerAccountService;
import dev.payflow.ledger.account.NormalBalance;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Double-entry posting for a payment capture (ARCHITECTURE_AND_PLAN.md §2.3.1). Called
 * synchronously by the Temporal {@code postToLedger} Activity, so a posting failure is visible to
 * the workflow immediately.
 */
@Service
public class LedgerPostingService {

  private static final double PLATFORM_FEE_RATE = 0.029;
  private static final String PLATFORM_BANK = "Platform Bank";
  private static final String PLATFORM_FEE_REVENUE = "Platform Fee Revenue";

  private final LedgerAccountService accountService;
  private final LedgerEntryRepository entryRepository;

  public LedgerPostingService(
      LedgerAccountService accountService, LedgerEntryRepository entryRepository) {
    this.accountService = accountService;
    this.entryRepository = entryRepository;
  }

  @Transactional
  public void postPaymentCapture(UUID paymentId, UUID merchantId, Money amount) {
    String transactionRef = paymentId.toString();
    if (entryRepository.existsForTransactionRef(transactionRef)) {
      return;
    }

    LedgerAccount platformBank =
        accountService.ensurePlatformAccount(PLATFORM_BANK, AccountType.ASSET, NormalBalance.DEBIT);
    LedgerAccount feeRevenue =
        accountService.ensurePlatformAccount(
            PLATFORM_FEE_REVENUE, AccountType.REVENUE, NormalBalance.CREDIT);
    LedgerAccount merchantPayable = accountService.ensureMerchantPayableAccount(merchantId);

    long feeMinor = Math.round(amount.amountMinor() * PLATFORM_FEE_RATE);
    long merchantShareMinor = amount.amountMinor() - feeMinor;

    postLeg(transactionRef, platformBank, merchantPayable, merchantShareMinor);
    postLeg(transactionRef, platformBank, feeRevenue, feeMinor);
  }

  /**
   * Double-entry reversal of a capture (ARCHITECTURE_AND_PLAN.md §2.3.2) — the exact mirror of
   * {@link #postPaymentCapture}, debit and credit sides swapped. Posted under the SAME {@code
   * transaction_ref} as the original capture: both the capture and its reversal stay in the
   * permanent ledger history, which is how double-entry bookkeeping records a correction (never
   * by deleting the original rows).
   */
  @Transactional
  public void postRefund(UUID paymentId, UUID merchantId, Money amount) {
    String transactionRef = paymentId.toString();

    LedgerAccount platformBank =
        accountService.ensurePlatformAccount(PLATFORM_BANK, AccountType.ASSET, NormalBalance.DEBIT);
    LedgerAccount feeRevenue =
        accountService.ensurePlatformAccount(
            PLATFORM_FEE_REVENUE, AccountType.REVENUE, NormalBalance.CREDIT);
    LedgerAccount merchantPayable = accountService.ensureMerchantPayableAccount(merchantId);

    long feeMinor = Math.round(amount.amountMinor() * PLATFORM_FEE_RATE);
    long merchantShareMinor = amount.amountMinor() - feeMinor;

    postLeg(transactionRef, merchantPayable, platformBank, merchantShareMinor);
    postLeg(transactionRef, feeRevenue, platformBank, feeMinor);
  }

  /**
   * Reverses a payment's capture only if the ledger actually posted one — a saga-compensated
   * capture (Milestone 2's {@code postToLedger} failure path) never wrote anything here in the
   * first place, so there is nothing to reverse and this is a safe no-op. Derives the merchant
   * and the originally-captured amount from the existing {@code ledger_entries} rows themselves
   * rather than trusting the Kafka payload, which doesn't carry either (see {@code
   * PaymentRefundedConsumer}'s Javadoc).
   */
  @Transactional
  public void reverseCaptureIfExists(UUID paymentId) {
    String transactionRef = paymentId.toString();
    List<LedgerEntry> captureEntries = entryRepository.findByTransactionRef(transactionRef);
    if (captureEntries.isEmpty()) {
      return;
    }

    long totalAmountMinor = captureEntries.stream().mapToLong(LedgerEntry::amountMinor).sum();
    UUID merchantId = resolveMerchantId(captureEntries);

    postRefund(paymentId, merchantId, Money.of(totalAmountMinor, Currency.USD));
  }

  /** Entries are grouped under one {@code transaction_ref}; used by {@code GET /ledger}. */
  public List<LedgerEntry> findEntriesForTransaction(String transactionRef) {
    return entryRepository.findByTransactionRef(transactionRef);
  }

  private UUID resolveMerchantId(List<LedgerEntry> entries) {
    for (LedgerEntry entry : entries) {
      LedgerAccount creditAccount = accountService.findById(entry.creditAccountId()).orElseThrow();
      if (creditAccount.merchantId() != null) {
        return creditAccount.merchantId();
      }
    }
    throw new IllegalStateException(
        "No merchant-scoped ledger entry found among: " + entries);
  }

  private void postLeg(
      String transactionRef, LedgerAccount debitAccount, LedgerAccount creditAccount, long amountMinor) {
    entryRepository.insertEntry(transactionRef, debitAccount.id(), creditAccount.id(), amountMinor);
    accountService.applyDebit(debitAccount, amountMinor);
    accountService.applyCredit(creditAccount, amountMinor);
  }
}
