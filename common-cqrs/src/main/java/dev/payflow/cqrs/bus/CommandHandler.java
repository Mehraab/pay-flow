package dev.payflow.cqrs.bus;

public interface CommandHandler<C extends Command<R>, R> {

  R handle(C command);
}
