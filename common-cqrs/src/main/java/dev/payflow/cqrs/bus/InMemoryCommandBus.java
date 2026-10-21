package dev.payflow.cqrs.bus;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reference {@link CommandBus} implementation: resolves a {@link CommandHandler} by the
 * command's runtime type and runs it through the registered {@link CommandInterceptor} chain, in
 * the order the interceptors were supplied — the first interceptor in the list runs outermost.
 *
 * <p>This is infrastructure, not a business feature, so it lives in common-cqrs rather than being
 * reimplemented per service. A Spring-based service wires its handler map and interceptor list as
 * beans and constructs one of these; common-cqrs itself has no opinion on how that wiring happens.
 */
public final class InMemoryCommandBus implements CommandBus {

  private final Map<Class<? extends Command<?>>, CommandHandler<?, ?>> handlers;
  private final List<CommandInterceptor> interceptors;

  public InMemoryCommandBus(
      Map<Class<? extends Command<?>>, CommandHandler<?, ?>> handlers,
      List<CommandInterceptor> interceptors) {
    this.handlers = Map.copyOf(handlers);
    this.interceptors = List.copyOf(interceptors);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> R dispatch(Command<R> command) {
    Objects.requireNonNull(command, "command");

    var handler = (CommandHandler<Command<R>, R>) handlers.get(command.getClass());
    if (handler == null) {
      throw new IllegalStateException(
          "No CommandHandler registered for " + command.getClass().getName());
    }

    CommandInvocation<Command<R>, R> invocation = handler::handle;
    for (int i = interceptors.size() - 1; i >= 0; i--) {
      var interceptor = interceptors.get(i);
      var next = invocation;
      invocation = cmd -> interceptor.intercept(cmd, next);
    }

    return invocation.proceed(command);
  }
}
