package dev.payflow.cqrs.domain;

import java.util.Objects;

/** Base for a domain object with identity but that is not, itself, an aggregate root. */
public abstract class Entity<ID> {

  private final ID id;

  protected Entity(ID id) {
    this.id = Objects.requireNonNull(id, "id");
  }

  public final ID id() {
    return id;
  }

  @Override
  public final boolean equals(Object o) {
    return o instanceof Entity<?> other && Objects.equals(id, other.id);
  }

  @Override
  public final int hashCode() {
    return Objects.hashCode(id);
  }
}
