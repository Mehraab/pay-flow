package dev.payflow.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

  private static final String SECRET = "test-secret-at-least-32-characters-long!!";

  @Test
  void issuedAccessTokenCarriesTheRoleAndMerchantIdClaims() {
    JwtTokenService service = new JwtTokenService(SECRET, 900);
    UUID merchantId = UUID.randomUUID();

    String token = service.issueAccessToken(merchantId.toString(), "ROLE_MERCHANT", merchantId);
    Claims claims = service.parseAndValidateAccessToken(token);

    assertThat(claims.getSubject()).isEqualTo(merchantId.toString());
    assertThat(claims.get("role", String.class)).isEqualTo("ROLE_MERCHANT");
    assertThat(claims.get("merchantId", String.class)).isEqualTo(merchantId.toString());
    assertThat(claims.get("token_type", String.class)).isEqualTo("ACCESS");
  }

  @Test
  void operatorTokensCarryNoMerchantIdClaim() {
    JwtTokenService service = new JwtTokenService(SECRET, 900);

    String token = service.issueAccessToken("operator-1", "ROLE_OPERATOR", null);
    Claims claims = service.parseAndValidateAccessToken(token);

    assertThat(claims.get("merchantId", String.class)).isNull();
  }

  @Test
  void anExpiredTokenFailsValidation() {
    JwtTokenService service = new JwtTokenService(SECRET, -1);

    String token = service.issueAccessToken("subject", "ROLE_OPERATOR", null);

    assertThatThrownBy(() -> service.parseAndValidateAccessToken(token))
        .isInstanceOf(ExpiredJwtException.class);
  }

  @Test
  void aTokenSignedWithADifferentSecretFailsValidation() {
    JwtTokenService issuer = new JwtTokenService(SECRET, 900);
    JwtTokenService verifier = new JwtTokenService("a-completely-different-32-char-secret!!", 900);

    String token = issuer.issueAccessToken("subject", "ROLE_OPERATOR", null);

    assertThatThrownBy(() -> verifier.parseAndValidateAccessToken(token)).isInstanceOf(RuntimeException.class);
  }
}
