package dev.payflow.orchestrator.activity;

import java.util.UUID;

/** Wire shape of Ledger Service's {@code POST /internal/ledger/post-capture} body. */
record PostLedgerCaptureRequest(UUID paymentId, UUID merchantId, long amountMinor, String currency) {}
