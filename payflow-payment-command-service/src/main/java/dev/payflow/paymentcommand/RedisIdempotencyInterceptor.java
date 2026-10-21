package dev.payflow.paymentcommand;

import dev.payflow.cqrs.bus.Command;
import dev.payflow.cqrs.bus.CommandInterceptor;
import dev.payflow.cqrs.bus.CommandInvocation;
import dev.payflow.cqrs.bus.IdempotentCommand;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Enforces BR-2 (never charge the same payment twice) once, centrally, for every command that
 * opts in via {@link IdempotentCommand} — not duplicated inside individual handlers (Rule 4).
 *
 * <p>Claims the key atomically with Redis {@code SETNX} (via {@code setIfAbsent}) before letting
 * the command proceed, so two concurrent requests with the same key can't both pass through. A
 * genuinely concurrent duplicate (the first request hasn't finished yet) fails fast rather than
 * blocking or silently double-processing — acceptable for Milestone 1; a bounded retry/backoff is
 * a reasonable Milestone 2+ refinement if concurrent retries turn out to be common in practice.
 */
@Component
public class RedisIdempotencyInterceptor implements CommandInterceptor {

  private static final Duration TTL = Duration.ofHours(24);
  private static final String IN_PROGRESS_SENTINEL = "IN_PROGRESS";

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public RedisIdempotencyInterceptor(StringRedisTemplate redis, ObjectMapper objectMapper) {
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <C extends Command<R>, R> R intercept(C command, CommandInvocation<C, R> next) {
    if (!(command instanceof IdempotentCommand<?> idempotentRaw)) {
      return next.proceed(command);
    }
    IdempotentCommand<R> idempotent = (IdempotentCommand<R>) idempotentRaw;

    String key = "idempotency:payment:" + idempotent.idempotencyKey();
    boolean claimed =
        Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, IN_PROGRESS_SENTINEL, TTL));

    if (!claimed) {
      String cached = redis.opsForValue().get(key);
      if (cached == null || IN_PROGRESS_SENTINEL.equals(cached)) {
        throw new IllegalStateException(
            "A request with idempotency key '" + idempotent.idempotencyKey() + "' is already in progress");
      }
      return objectMapper.readValue(cached, idempotent.resultType());
    }

    R result = next.proceed(command);
    redis.opsForValue().set(key, objectMapper.writeValueAsString(result), TTL);
    return result;
  }
}
