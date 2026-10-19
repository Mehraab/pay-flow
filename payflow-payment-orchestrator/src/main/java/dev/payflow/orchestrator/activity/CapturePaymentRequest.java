package dev.payflow.orchestrator.activity;

/** Wire shape of Payment Command's {@code POST /internal/payments/{id}/capture} body. */
record CapturePaymentRequest(String providerRef) {}
