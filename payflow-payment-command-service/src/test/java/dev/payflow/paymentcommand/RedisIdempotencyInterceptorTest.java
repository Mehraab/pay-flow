package dev.payflow.paymentcommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.payflow.cqrs.bus.Command;
import dev.payflow.cqrs.bus.CommandInvocation;
import dev.payflow.cqrs.bus.IdempotentCommand;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class RedisIdempotencyInterceptorTest {

  private record Result(UUID value) {}

  private record Idempotent(String idempotencyKey) implements IdempotentCommand<Result> {
    @Override
    public Class<Result> resultType() {
      return Result.class;
    }
  }

  private record Plain() implements Command<String> {}

  private StringRedisTemplate redis;
  private ValueOperations<String, String> valueOps;
  private final ObjectMapper objectMapper = JsonMapper.builder().build();
  private RedisIdempotencyInterceptor interceptor;

  @BeforeEach
  void setUp() {
    redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> ops = mock(ValueOperations.class);
    valueOps = ops;
    when(redis.opsForValue()).thenReturn(valueOps);
    interceptor = new RedisIdempotencyInterceptor(redis, objectMapper);
  }

  @Test
  void commandsThatAreNotIdempotentPassThroughUnchanged() {
    CommandInvocation<Plain, String> next = cmd -> "handled";

    String result = interceptor.intercept(new Plain(), next);

    assertThat(result).isEqualTo("handled");
    verify(valueOps, never()).setIfAbsent(any(), any(), any(Duration.class));
  }

  @Test
  void aFreshKeyClaimsAndCachesTheResult() {
    when(valueOps.setIfAbsent(eq("idempotency:payment:demo-001"), eq("IN_PROGRESS"), any(Duration.class)))
        .thenReturn(true);
    var expected = new Result(UUID.randomUUID());
    CommandInvocation<Idempotent, Result> next = cmd -> expected;

    Result result = interceptor.intercept(new Idempotent("demo-001"), next);

    assertThat(result).isEqualTo(expected);
    verify(valueOps)
        .set(eq("idempotency:payment:demo-001"), eq(objectMapper.writeValueAsString(expected)), any(Duration.class));
  }

  @Test
  void aPreviouslyCompletedKeyShortCircuitsAndReturnsTheCachedResult() {
    var cached = new Result(UUID.randomUUID());
    when(valueOps.setIfAbsent(eq("idempotency:payment:demo-002"), eq("IN_PROGRESS"), any(Duration.class)))
        .thenReturn(false);
    when(valueOps.get("idempotency:payment:demo-002")).thenReturn(objectMapper.writeValueAsString(cached));
    CommandInvocation<Idempotent, Result> next =
        cmd -> {
          throw new AssertionError("next.proceed() must not run on a cache hit");
        };

    Result result = interceptor.intercept(new Idempotent("demo-002"), next);

    assertThat(result).isEqualTo(cached);
  }

  @Test
  void aConcurrentInProgressKeyFailsFastRatherThanDoubleProcessing() {
    when(valueOps.setIfAbsent(eq("idempotency:payment:demo-003"), eq("IN_PROGRESS"), any(Duration.class)))
        .thenReturn(false);
    when(valueOps.get("idempotency:payment:demo-003")).thenReturn("IN_PROGRESS");
    CommandInvocation<Idempotent, Result> next =
        cmd -> {
          throw new AssertionError("next.proceed() must not run while another request is in-flight");
        };

    assertThatThrownBy(() -> interceptor.intercept(new Idempotent("demo-003"), next))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("demo-003");
  }
}
