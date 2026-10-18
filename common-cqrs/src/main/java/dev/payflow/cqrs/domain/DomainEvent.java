package dev.payflow.cqrs.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Every domain event is a record that implements this interface and carries an {@link
 * EventMetadata} component — composition, not inheritance, so events stay plain records.
 */
public interface DomainEvent {

  EventMetadata metadata();

  default UUID eventId() {
    return metadata().eventId();
  }

  default Instant occurredOn() {
    return metadata().occurredOn();
  }

  default UUID correlationId() {
    return metadata().correlationId();
  }

  default UUID causationId() {
    return metadata().causationId();
  }

  default Object aggregateId() {
    return metadata().aggregateId();
  }
}
