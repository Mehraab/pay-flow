package dev.payflow.cqrs.bus;

/** Marker for a read-intent message, parameterized by its own result type. */
public interface Query<R> {}
