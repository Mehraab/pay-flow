package dev.payflow.notification.web;

import java.util.UUID;

public record DispatchWebhookRequest(UUID merchantId, String eventType, String payload) {}
