package dev.payflow.ledger.posting;

import java.time.Instant;
import java.util.UUID;

/** A row of {@code ledger_entries}. */
public record LedgerEntry(
    UUID id,
    String transactionRef,
    UUID debitAccountId,
    UUID creditAccountId,
    long amountMinor,
    Instant postedAt) {}
