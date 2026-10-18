package dev.payflow.cqrs.exception;

public final class ConcurrencyException extends DomainException {

  public ConcurrencyException(Object aggregateId, long expectedVersion, long actualVersion) {
    super(
        "Concurrent modification of aggregate %s: expected version %d but was %d"
            .formatted(aggregateId, expectedVersion, actualVersion));
  }
}
