package dev.payflow.apigateway.security;

import dev.payflow.apigateway.web.TokenResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Access/refresh token lifecycle (BR-11): issuance, rotation on refresh, and revocation. */
@Service
public class AuthService {

  private final JwtTokenService jwtTokenService;
  private final RefreshTokenStore refreshTokenStore;
  private final long accessTokenTtlSeconds;
  private final Duration refreshTokenTtl;

  public AuthService(
      JwtTokenService jwtTokenService,
      RefreshTokenStore refreshTokenStore,
      @Value("${payflow.security.jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds,
      @Value("${payflow.security.jwt.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds) {
    this.jwtTokenService = jwtTokenService;
    this.refreshTokenStore = refreshTokenStore;
    this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    this.refreshTokenTtl = Duration.ofSeconds(refreshTokenTtlSeconds);
  }

  public TokenResponse login(String role, UUID merchantId) {
    String accessToken = issueAccessToken(role, merchantId);
    String refreshToken = UUID.randomUUID().toString();
    refreshTokenStore.save(refreshToken, new RefreshTokenData(role, merchantId), refreshTokenTtl);
    return new TokenResponse(accessToken, refreshToken, "Bearer", accessTokenTtlSeconds);
  }

  public TokenResponse refresh(String refreshToken) {
    RefreshTokenData data =
        refreshTokenStore
            .find(refreshToken)
            .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid or expired"));
    refreshTokenStore.revoke(refreshToken);

    String newRefreshToken = UUID.randomUUID().toString();
    refreshTokenStore.save(newRefreshToken, data, refreshTokenTtl);

    String accessToken = issueAccessToken(data.role(), data.merchantId());
    return new TokenResponse(accessToken, newRefreshToken, "Bearer", accessTokenTtlSeconds);
  }

  public void revoke(String refreshToken) {
    refreshTokenStore.revoke(refreshToken);
  }

  private String issueAccessToken(String role, UUID merchantId) {
    String subject = merchantId != null ? merchantId.toString() : role;
    return jwtTokenService.issueAccessToken(subject, role, merchantId);
  }
}
