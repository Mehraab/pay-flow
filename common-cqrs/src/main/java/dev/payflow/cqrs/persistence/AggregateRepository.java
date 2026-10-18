package dev.payflow.cqrs.persistence;

import dev.payflow.cqrs.domain.AggregateRoot;
import dev.payflow.cqrs.domain.DomainEvent;
import dev.payflow.cqrs.exception.AggregateNotFoundException;
import java.util.Optional;

/**
 * The ONLY way application code reads or writes an aggregate. No service may depend on JPA,
 * jOOQ, JDBC, or any other persistence API directly — that dependency lives entirely inside the
 * adapter that implements this interface (see AGENT_AND_DEV_GUIDELINES.md, Rule 1 and Rule 2).
 *
 * @param <AR> the aggregate root type
 * @param <ID> the aggregate's identity type
 * @param <E> the aggregate's domain event type
 */
public interface AggregateRepository<AR extends AggregateRoot<ID, E>, ID, E extends DomainEvent> {

  Optional<AR> findById(ID id);

  /**
   * Persists the aggregate's current state AND appends every event returned by {@link
   * AggregateRoot#pullUncommittedEvents()} to that bounded context's outbox table — in ONE
   * database transaction (the Transactional Outbox pattern). Implementations MUST enforce
   * optimistic locking: if the stored row's version no longer matches the version this aggregate
   * was loaded at, throw {@link dev.payflow.cqrs.exception.ConcurrencyException} and write
   * nothing.
   */
  void save(AR aggregate);

  default AR getById(ID id) {
    return findById(id).orElseThrow(() -> new AggregateNotFoundException(id));
  }
}
