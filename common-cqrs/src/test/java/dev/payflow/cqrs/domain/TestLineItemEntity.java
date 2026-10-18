package dev.payflow.cqrs.domain;

import java.util.UUID;

/**
 * Throwaway {@link Entity} subtype used only to prove {@link Entity#equals}'s identity
 * semantics. NOT a PayFlow business type.
 */
public final class TestLineItemEntity extends Entity<UUID> {

  public TestLineItemEntity(UUID id) {
    super(id);
  }
}
