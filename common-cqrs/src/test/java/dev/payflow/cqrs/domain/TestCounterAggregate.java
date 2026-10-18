package dev.payflow.cqrs.domain;

import java.util.UUID;

/**
 * Throwaway aggregate used only to prove the common-cqrs harness works. NOT a PayFlow business
 * type — deleted before Milestone 1 starts (see ARCHITECTURE_AND_PLAN.md, Milestone 0.5, task 5).
 */
public final class TestCounterAggregate extends AggregateRoot<UUID, TestCounterAggregate.CounterEvent> {

  private int value;

  public TestCounterAggregate(UUID id, long version) {
    super(id, version);
  }

  public void increment(int by) {
    raise(new Incremented(EventMetadata.next(id(), UUID.randomUUID(), null), by));
  }

  public int value() {
    return value;
  }

  @Override
  protected void apply(CounterEvent event) {
    switch (event) {
      case Incremented e -> this.value += e.by();
    }
  }

  public sealed interface CounterEvent extends DomainEvent permits Incremented {}

  public record Incremented(EventMetadata metadata, int by) implements CounterEvent {}
}
