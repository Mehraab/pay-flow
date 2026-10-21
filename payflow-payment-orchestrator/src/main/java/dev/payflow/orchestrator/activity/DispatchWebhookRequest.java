package dev.payflow.orchestrator.activity;

import java.util.UUID;

/** Wire shape of Notification Service's {@code POST /internal/webhooks/dispatch} body. */
record DispatchWebhookRequest(UUID merchantId, String eventType, String payload) {}
