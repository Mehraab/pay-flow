package dev.payflow.ledger.web;

import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
    UUID id,
    String transactionRef,
    UUID debitAccountId,
    UUID creditAccountId,
    long amountMinor,
    Instant postedAt,
    String status) {

  /** Preserves the pre-BE-06 shape so older call sites (and tests) keep compiling unchanged. */
  public LedgerEntryResponse(
      UUID id, String transactionRef, UUID debitAccountId, UUID creditAccountId, long amountMinor, Instant postedAt) {
    this(id, transactionRef, debitAccountId, creditAccountId, amountMinor, postedAt, "POSTED");
  }
}
