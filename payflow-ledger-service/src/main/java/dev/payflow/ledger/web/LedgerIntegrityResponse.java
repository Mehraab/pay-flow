package dev.payflow.ledger.web;

public record LedgerIntegrityResponse(long totalDebitsMinor, long totalCreditsMinor, boolean balanced) {}
