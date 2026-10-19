package dev.payflow.notification.delivery;

import java.util.UUID;

public record WebhookEndpoint(UUID id, UUID merchantId, String url, String signingSecret, boolean active) {}
