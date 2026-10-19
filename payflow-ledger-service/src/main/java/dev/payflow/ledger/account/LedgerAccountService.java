package dev.payflow.ledger.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LedgerAccountService {

  public static final String MERCHANT_PAYABLE = "Merchant Payable";

  private final LedgerAccountRepository repository;

  public LedgerAccountService(LedgerAccountRepository repository) {
    this.repository = repository;
  }

  public LedgerAccount ensurePlatformAccount(
      String accountName, AccountType type, NormalBalance normalBalance) {
    return repository
        .findPlatformAccount(accountName)
        .orElseGet(() -> repository.insertPlatformAccount(type, accountName, normalBalance));
  }

  public LedgerAccount ensureMerchantPayableAccount(UUID merchantId) {
    return repository
        .findMerchantAccount(merchantId, MERCHANT_PAYABLE)
        .orElseGet(
            () ->
                repository
                    .insertMerchantAccountIfAbsent(
                        merchantId, AccountType.LIABILITY, MERCHANT_PAYABLE, NormalBalance.CREDIT)
                    .orElseGet(
                        () ->
                            repository
                                .findMerchantAccount(merchantId, MERCHANT_PAYABLE)
                                .orElseThrow(
                                    () ->
                                        new IllegalStateException(
                                            "Failed to create or find "
                                                + MERCHANT_PAYABLE
                                                + " account for merchant "
                                                + merchantId))));
  }

  public Optional<LedgerAccount> findPlatformAccount(String accountName) {
    return repository.findPlatformAccount(accountName);
  }

  public Optional<LedgerAccount> findMerchantAccount(UUID merchantId, String accountName) {
    return repository.findMerchantAccount(merchantId, accountName);
  }

  /** Posts a debit leg: balance moves with the amount when the account's normal side is DEBIT. */
  public void applyDebit(LedgerAccount account, long amountMinor) {
    long delta = account.normalBalance() == NormalBalance.DEBIT ? amountMinor : -amountMinor;
    repository.adjustBalance(account.id(), delta);
  }

  /** Posts a credit leg: balance moves with the amount when the account's normal side is CREDIT. */
  public void applyCredit(LedgerAccount account, long amountMinor) {
    long delta = account.normalBalance() == NormalBalance.CREDIT ? amountMinor : -amountMinor;
    repository.adjustBalance(account.id(), delta);
  }
}
