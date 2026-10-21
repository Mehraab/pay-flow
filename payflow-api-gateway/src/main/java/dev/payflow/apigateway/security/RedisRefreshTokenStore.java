package dev.payflow.apigateway.security;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Redis-backed {@link RefreshTokenStore} — one JSON value per token under {@code auth:refresh:*}. */
@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

  private static final String KEY_PREFIX = "auth:refresh:";

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public RedisRefreshTokenStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  @Override
  public void save(String refreshToken, RefreshTokenData data, Duration ttl) {
    redis.opsForValue().set(KEY_PREFIX + refreshToken, objectMapper.writeValueAsString(data), ttl);
  }

  @Override
  public Optional<RefreshTokenData> find(String refreshToken) {
    String json = redis.opsForValue().get(KEY_PREFIX + refreshToken);
    if (json == null) {
      return Optional.empty();
    }
    return Optional.of(objectMapper.readValue(json, RefreshTokenData.class));
  }

  @Override
  public void revoke(String refreshToken) {
    redis.delete(KEY_PREFIX + refreshToken);
  }
}
