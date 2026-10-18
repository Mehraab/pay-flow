package dev.payflow.cqrs.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemoryCommandBusTest {

  private record Greet(String name) implements Command<String> {}

  @Test
  void dispatchesToTheHandlerRegisteredForTheCommandType() {
    CommandHandler<Greet, String> handler = cmd -> "hello " + cmd.name();
    var bus = new InMemoryCommandBus(Map.of(Greet.class, handler), List.of());

    String result = bus.dispatch(new Greet("world"));

    assertThat(result).isEqualTo("hello world");
  }

  @Test
  void runsInterceptorsInOrderWithTheFirstOutermost() {
    var trace = new ArrayList<String>();
    CommandInterceptor outer =
        new CommandInterceptor() {
          @Override
          public <C extends Command<R>, R> R intercept(C command, CommandInvocation<C, R> next) {
            trace.add("outer-before");
            R result = next.proceed(command);
            trace.add("outer-after");
            return result;
          }
        };
    CommandInterceptor inner =
        new CommandInterceptor() {
          @Override
          public <C extends Command<R>, R> R intercept(C command, CommandInvocation<C, R> next) {
            trace.add("inner-before");
            R result = next.proceed(command);
            trace.add("inner-after");
            return result;
          }
        };
    CommandHandler<Greet, String> handler =
        cmd -> {
          trace.add("handler");
          return "ok";
        };
    var bus = new InMemoryCommandBus(Map.of(Greet.class, handler), List.of(outer, inner));

    bus.dispatch(new Greet("world"));

    assertThat(trace)
        .containsExactly("outer-before", "inner-before", "handler", "inner-after", "outer-after");
  }

  @Test
  void dispatchingAnUnregisteredCommandFailsFast() {
    var bus = new InMemoryCommandBus(Map.of(), List.of());

    assertThatThrownBy(() -> bus.dispatch(new Greet("world")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Greet");
  }
}
