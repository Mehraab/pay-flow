package dev.payflow.apigateway.security;

import java.time.Duration;
import java.util.Optional;

/** Storage port for refresh tokens, backed by Redis in production ({@link RedisRefreshTokenStore}). */
public interface RefreshTokenStore {

  void save(String refreshToken, RefreshTokenData data, Duration ttl);

  Optional<RefreshTokenData> find(String refreshToken);

  void revoke(String refreshToken);
}
