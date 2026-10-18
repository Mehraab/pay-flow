package dev.payflow.cqrs.domain;

import java.util.UUID;

/**
 * A second throwaway aggregate type, structurally identical to {@link TestCounterAggregate} but a
 * distinct class — exists solely to prove the cross-aggregate equality fix in {@link
 * AggregateRoot#equals}: two aggregates of different types must never be equal, even if they
 * share an ID. NOT a PayFlow business type.
 */
public final class AnotherTestAggregate extends AggregateRoot<UUID, AnotherTestAggregate.MarkerEvent> {

  public AnotherTestAggregate(UUID id, long version) {
    super(id, version);
  }

  @Override
  protected void apply(MarkerEvent event) {
    // no state to mutate — this aggregate exists only to carry an id for the equality test
  }

  public sealed interface MarkerEvent extends DomainEvent permits Marked {}

  public record Marked(EventMetadata metadata) implements MarkerEvent {}
}
