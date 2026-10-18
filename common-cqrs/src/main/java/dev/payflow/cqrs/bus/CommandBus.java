package dev.payflow.cqrs.bus;

/** The single entry point for every write in the system — see Rule 2. */
public interface CommandBus {

  <C extends Command, R> R dispatch(C command);
}
