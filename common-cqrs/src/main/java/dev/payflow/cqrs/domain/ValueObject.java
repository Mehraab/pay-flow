package dev.payflow.cqrs.domain;

/**
 * Marker interface for immutable, structurally-equal domain values.
 *
 * <p>Implement with a {@code record} — records already give you the equality and immutability a
 * value object needs; this interface exists so the type system (and ArchUnit) can distinguish
 * value objects from entities at compile time and in architecture tests.
 *
 * <p>e.g. {@code public record Money(long amountMinor, Currency currency) implements ValueObject
 * {}}
 */
public interface ValueObject {}
