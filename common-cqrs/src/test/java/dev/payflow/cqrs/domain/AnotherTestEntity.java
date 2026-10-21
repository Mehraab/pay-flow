package dev.payflow.cqrs.domain;

import java.util.UUID;

/**
 * A second throwaway {@link Entity} type, structurally identical to {@link TestLineItemEntity}
 * but a distinct class — exists solely to prove the cross-entity equality fix: two entities of
 * different types must never be equal, even if they share an ID. NOT a PayFlow business type.
 */
public final class AnotherTestEntity extends Entity<UUID> {

  public AnotherTestEntity(UUID id) {
    super(id);
  }
}
