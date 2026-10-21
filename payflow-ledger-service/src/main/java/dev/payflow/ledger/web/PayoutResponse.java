package dev.payflow.ledger.web;

import java.time.Instant;
import java.util.UUID;

public record PayoutResponse(
    UUID id, UUID merchantId, long amountMinor, String currency, String status, Instant createdAt) {}
