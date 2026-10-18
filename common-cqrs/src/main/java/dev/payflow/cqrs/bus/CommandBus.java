package dev.payflow.cqrs.bus;

/** The single entry point for every write in the system — see Rule 2. */
public interface CommandBus {

  <R> R dispatch(Command<R> command);
}
