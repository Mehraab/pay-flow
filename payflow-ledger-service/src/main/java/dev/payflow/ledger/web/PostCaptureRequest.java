package dev.payflow.ledger.web;

import java.util.UUID;

public record PostCaptureRequest(UUID paymentId, UUID merchantId, long amountMinor, String currency) {}
