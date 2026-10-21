package dev.payflow.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Issues and validates short-lived JWT access tokens (BR-11). */
@Component
public class JwtTokenService {

  private static final String CLAIM_ROLE = "role";
  private static final String CLAIM_MERCHANT_ID = "merchantId";
  private static final String CLAIM_TOKEN_TYPE = "token_type";
  private static final String TOKEN_TYPE_ACCESS = "ACCESS";

  private final SecretKey signingKey;
  private final Duration accessTokenTtl;

  public JwtTokenService(
      @Value("${payflow.security.jwt.secret}") String secret,
      @Value("${payflow.security.jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds) {
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTokenTtl = Duration.ofSeconds(accessTokenTtlSeconds);
  }

  public String issueAccessToken(String subject, String role, UUID merchantId) {
    Instant now = Instant.now();
    JwtBuilder builder =
        Jwts.builder()
            .subject(subject)
            .claim(CLAIM_ROLE, role)
            .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTokenTtl)));
    if (merchantId != null) {
      builder.claim(CLAIM_MERCHANT_ID, merchantId.toString());
    }
    return builder.signWith(signingKey).compact();
  }

  public Claims parseAndValidateAccessToken(String token) {
    Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    if (!TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
      throw new InvalidTokenException("Token is not an access token");
    }
    return claims;
  }
}
