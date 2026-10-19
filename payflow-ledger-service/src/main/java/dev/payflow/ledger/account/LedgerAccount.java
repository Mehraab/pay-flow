package dev.payflow.ledger.account;

import java.util.UUID;

/** A row of {@code ledger_accounts}. {@code merchantId} is {@code null} for platform accounts. */
public record LedgerAccount(
    UUID id,
    UUID merchantId,
    AccountType accountType,
    String accountName,
    NormalBalance normalBalance,
    long balanceMinor) {}
