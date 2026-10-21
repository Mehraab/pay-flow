package dev.payflow.ledger.web;

import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
    UUID id,
    String transactionRef,
    UUID debitAccountId,
    UUID creditAccountId,
    long amountMinor,
    Instant postedAt) {}
