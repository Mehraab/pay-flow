package dev.payflow.cqrs.persistence;

import dev.payflow.cqrs.domain.AggregateRoot;
import dev.payflow.cqrs.domain.DomainEvent;
import dev.payflow.cqrs.exception.ConcurrencyException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal in-memory implementation of {@link AggregateRepository}, used only to prove the port's
 * contract (atomic save + optimistic locking) in common-cqrs's own test suite. NOT a real
 * adapter — real adapters are Postgres-backed and live in each service, starting Milestone 1.
 */
final class InMemoryAggregateRepository<AR extends AggregateRoot<ID, E>, ID, E extends DomainEvent>
    implements AggregateRepository<AR, ID, E> {

  private final Map<ID, AR> store = new HashMap<>();

  @Override
  public Optional<AR> findById(ID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public void save(AR aggregate) {
    var stored = store.get(aggregate.id());
    if (stored != null && stored.version() != aggregate.version()) {
      throw new ConcurrencyException(aggregate.id(), aggregate.version(), stored.version());
    }
    aggregate.incrementVersion();
    aggregate.pullUncommittedEvents(); // simulate the outbox append + drain, in the same "transaction"
    store.put(aggregate.id(), aggregate);
  }
}
