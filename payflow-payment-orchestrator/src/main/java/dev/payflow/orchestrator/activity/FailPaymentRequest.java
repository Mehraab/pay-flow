package dev.payflow.orchestrator.activity;

/** Wire shape of Payment Command's {@code POST /internal/payments/{id}/fail} body. */
record FailPaymentRequest(String declineReason) {}
