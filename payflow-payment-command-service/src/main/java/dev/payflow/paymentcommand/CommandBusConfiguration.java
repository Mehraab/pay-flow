package dev.payflow.paymentcommand;

import dev.payflow.cqrs.bus.Command;
import dev.payflow.cqrs.bus.CommandBus;
import dev.payflow.cqrs.bus.CommandHandler;
import dev.payflow.cqrs.bus.CommandInterceptor;
import dev.payflow.cqrs.bus.InMemoryCommandBus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;

/**
 * Wires every {@link CommandHandler} bean into the {@link CommandBus} by the {@code Command} type
 * it declares in its own generic signature — resolved once at startup via {@link ResolvableType},
 * not hardcoded here, so a new handler only needs {@code @Component}, nothing registered by hand.
 */
@Configuration
public class CommandBusConfiguration {

  @Bean
  public CommandBus commandBus(
      List<CommandHandler<?, ?>> handlers, List<CommandInterceptor> interceptors) {
    Map<Class<? extends Command<?>>, CommandHandler<?, ?>> handlersByCommandType = new HashMap<>();
    for (CommandHandler<?, ?> handler : handlers) {
      Class<?> commandType =
          ResolvableType.forClass(handler.getClass()).as(CommandHandler.class).getGeneric(0).resolve();
      if (commandType == null) {
        throw new IllegalStateException(
            "Could not resolve the Command type handled by " + handler.getClass());
      }
      @SuppressWarnings("unchecked")
      Class<? extends Command<?>> key = (Class<? extends Command<?>>) commandType;
      handlersByCommandType.put(key, handler);
    }
    return new InMemoryCommandBus(handlersByCommandType, interceptors);
  }
}
