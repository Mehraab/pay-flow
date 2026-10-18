package dev.payflow.cqrs.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Base for every aggregate root. Owns identity, an optimistic-locking {@code version}, and the
 * uncommitted-event buffer that the outbox is populated from (see {@code
 * AggregateRepository#save}). Domain state changes ONLY by raising an event and applying it to
 * itself — there are no public setters, here or on any subclass.
 */
public abstract class AggregateRoot<ID, E extends DomainEvent> {

  private final ID id;
  private long version;
  private final List<E> uncommittedEvents = new ArrayList<>();

  protected AggregateRoot(ID id, long version) {
    this.id = Objects.requireNonNull(id, "id");
    this.version = version;
  }

  public final ID id() {
    return id;
  }

  public final long version() {
    return version;
  }

  /** Records the event as uncommitted AND immediately mutates state via {@link #apply}. */
  protected final void raise(E event) {
    uncommittedEvents.add(event);
    apply(event);
  }

  /** Subclasses implement this as a pattern-matching switch over their event type hierarchy. */
  protected abstract void apply(E event);

  /** Called by the repository adapter after a successful save — never by application code. */
  public final List<E> pullUncommittedEvents() {
    var events = List.copyOf(uncommittedEvents);
    uncommittedEvents.clear();
    return events;
  }

  /** Called by the repository adapter after a successful save, in the same transaction. */
  public final void incrementVersion() {
    this.version++;
  }

  @Override
  public final boolean equals(Object o) {
    return o instanceof AggregateRoot<?, ?> other && Objects.equals(id, other.id);
  }

  @Override
  public final int hashCode() {
    return Objects.hashCode(id);
  }
}
