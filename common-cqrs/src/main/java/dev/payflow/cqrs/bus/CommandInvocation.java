package dev.payflow.cqrs.bus;

@FunctionalInterface
public interface CommandInvocation<C extends Command<R>, R> {

  R proceed(C command);
}
