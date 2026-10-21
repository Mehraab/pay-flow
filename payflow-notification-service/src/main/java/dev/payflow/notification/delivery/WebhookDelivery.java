package dev.payflow.notification.delivery;

import java.time.Instant;
import java.util.UUID;

public record WebhookDelivery(
    UUID id,
    UUID endpointId,
    String eventType,
    String payload,
    String status,
    int attemptCount,
    Integer responseCode,
    Instant lastAttemptAt,
    Instant nextRetryAt) {}
