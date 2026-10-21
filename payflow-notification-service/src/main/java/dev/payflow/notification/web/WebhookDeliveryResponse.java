package dev.payflow.notification.web;

import dev.payflow.notification.delivery.WebhookDelivery;
import java.time.Instant;
import java.util.UUID;

public record WebhookDeliveryResponse(
    UUID id,
    UUID endpointId,
    String eventType,
    String status,
    int attemptCount,
    Integer responseCode,
    Instant lastAttemptAt,
    Instant nextRetryAt) {

  public static WebhookDeliveryResponse from(WebhookDelivery delivery) {
    return new WebhookDeliveryResponse(
        delivery.id(),
        delivery.endpointId(),
        delivery.eventType(),
        delivery.status(),
        delivery.attemptCount(),
        delivery.responseCode(),
        delivery.lastAttemptAt(),
        delivery.nextRetryAt());
  }
}
