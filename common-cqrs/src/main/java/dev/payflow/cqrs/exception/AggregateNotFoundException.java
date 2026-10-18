package dev.payflow.cqrs.exception;

public final class AggregateNotFoundException extends DomainException {

  public AggregateNotFoundException(Object aggregateId) {
    super("Aggregate not found: " + aggregateId);
  }
}
