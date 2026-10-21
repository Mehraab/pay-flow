package dev.payflow.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.payflow.apigateway.web.TokenResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

  private static final String SECRET = "test-secret-at-least-32-characters-long!!";
  private static final long ACCESS_TTL_SECONDS = 900;
  private static final long REFRESH_TTL_SECONDS = 604_800;

  private final JwtTokenService jwtTokenService = new JwtTokenService(SECRET, ACCESS_TTL_SECONDS);
  private RefreshTokenStore refreshTokenStore;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    refreshTokenStore = mock(RefreshTokenStore.class);
    authService = new AuthService(jwtTokenService, refreshTokenStore, ACCESS_TTL_SECONDS, REFRESH_TTL_SECONDS);
  }

  @Test
  void loginIssuesAnAccessTokenAndPersistsARefreshToken() {
    UUID merchantId = UUID.randomUUID();

    TokenResponse response = authService.login("ROLE_MERCHANT", merchantId);

    assertThat(response.accessToken()).isNotBlank();
    assertThat(response.refreshToken()).isNotBlank();
    assertThat(response.tokenType()).isEqualTo("Bearer");
    assertThat(response.expiresIn()).isEqualTo(ACCESS_TTL_SECONDS);
    verify(refreshTokenStore)
        .save(
            eq(response.refreshToken()),
            eq(new RefreshTokenData("ROLE_MERCHANT", merchantId)),
            eq(Duration.ofSeconds(REFRESH_TTL_SECONDS)));
  }

  @Test
  void refreshRotatesTheRefreshTokenAndIssuesANewAccessToken() {
    UUID merchantId = UUID.randomUUID();
    RefreshTokenData data = new RefreshTokenData("ROLE_MERCHANT", merchantId);
    when(refreshTokenStore.find("old-token")).thenReturn(Optional.of(data));

    TokenResponse response = authService.refresh("old-token");

    assertThat(response.refreshToken()).isNotEqualTo("old-token");
    verify(refreshTokenStore).revoke("old-token");
    verify(refreshTokenStore)
        .save(eq(response.refreshToken()), eq(data), eq(Duration.ofSeconds(REFRESH_TTL_SECONDS)));
  }

  @Test
  void refreshThrowsAnInvalidTokenExceptionWhenTheRefreshTokenIsUnknown() {
    when(refreshTokenStore.find("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.refresh("missing")).isInstanceOf(InvalidTokenException.class);

    verify(refreshTokenStore, never()).revoke(any());
  }

  @Test
  void revokeDelegatesToTheStore() {
    authService.revoke("some-token");

    verify(refreshTokenStore).revoke("some-token");
  }
}
