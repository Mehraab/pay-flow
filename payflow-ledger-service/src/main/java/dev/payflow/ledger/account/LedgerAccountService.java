package dev.payflow.ledger.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LedgerAccountService {

  public static final String MERCHANT_PAYABLE = "Merchant Payable";
  public static final String PAYOUT_IN_TRANSIT = "Payout In-Transit";

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
    return ensureMerchantAccount(merchantId, MERCHANT_PAYABLE, AccountType.LIABILITY, NormalBalance.CREDIT);
  }

  public LedgerAccount ensurePayoutInTransitAccount(UUID merchantId) {
    return ensureMerchantAccount(merchantId, PAYOUT_IN_TRANSIT, AccountType.LIABILITY, NormalBalance.CREDIT);
  }

  private LedgerAccount ensureMerchantAccount(
      UUID merchantId, String accountName, AccountType type, NormalBalance normalBalance) {
    return repository
        .findMerchantAccount(merchantId, accountName)
        .orElseGet(
            () ->
                repository
                    .insertMerchantAccountIfAbsent(merchantId, type, accountName, normalBalance)
                    .orElseGet(
                        () ->
                            repository
                                .findMerchantAccount(merchantId, accountName)
                                .orElseThrow(
                                    () ->
                                        new IllegalStateException(
                                            "Failed to create or find "
                                                + accountName
                                                + " account for merchant "
                                                + merchantId))));
  }

  public Optional<LedgerAccount> findPlatformAccount(String accountName) {
    return repository.findPlatformAccount(accountName);
  }

  public Optional<LedgerAccount> findMerchantAccount(UUID merchantId, String accountName) {
    return repository.findMerchantAccount(merchantId, accountName);
  }

  public Optional<LedgerAccount> findById(UUID accountId) {
    return repository.findById(accountId);
  }

  /**
   * Resolves a merchant-scoped account by name/slug without knowing which merchant — only safe
   * when exactly one merchant has an account by that name; returns empty both when none match
   * and when more than one does, rather than guessing.
   */
  public Optional<LedgerAccount> findUniqueMerchantAccountByNameOrSlug(String accountName) {
    List<LedgerAccount> matches = repository.findMerchantAccountsByNameOrSlug(accountName);
    return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
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
