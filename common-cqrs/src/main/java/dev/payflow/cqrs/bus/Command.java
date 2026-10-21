package dev.payflow.cqrs.bus;

/** Marker for a write-intent message, parameterized by its result type. */
public interface Command<R> {}
