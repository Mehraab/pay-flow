package dev.payflow.ledger.payout;

import java.time.Instant;
import java.util.UUID;

public record Payout(
    UUID id,
    UUID merchantId,
    long amountMinor,
    String currency,
    PayoutStatus status,
    Instant createdAt,
    Instant settledAt) {}
