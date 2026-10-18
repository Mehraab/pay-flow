package dev.payflow.cqrs.domain;

import java.time.Instant;
import java.util.UUID;

/** Tracing metadata every domain event carries, independent of its own payload. */
public record EventMetadata(
    UUID eventId,
    Instant occurredOn,
    UUID correlationId,
    UUID causationId, // nullable: null for the first event in a chain
    Object aggregateId) {

  public static EventMetadata next(Object aggregateId, UUID correlationId, UUID causationId) {
    return new EventMetadata(UUID.randomUUID(), Instant.now(), correlationId, causationId, aggregateId);
  }
}
