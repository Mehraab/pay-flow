package dev.payflow.ledger.posting;

import dev.payflow.common.domain.Money;
import dev.payflow.ledger.account.AccountType;
import dev.payflow.ledger.account.LedgerAccount;
import dev.payflow.ledger.account.LedgerAccountService;
import dev.payflow.ledger.account.NormalBalance;
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

  private void postLeg(
      String transactionRef, LedgerAccount debitAccount, LedgerAccount creditAccount, long amountMinor) {
    entryRepository.insertEntry(transactionRef, debitAccount.id(), creditAccount.id(), amountMinor);
    accountService.applyDebit(debitAccount, amountMinor);
    accountService.applyCredit(creditAccount, amountMinor);
  }
}
