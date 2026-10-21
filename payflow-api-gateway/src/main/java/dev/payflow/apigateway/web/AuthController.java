package dev.payflow.apigateway.web;

import dev.payflow.apigateway.security.AuthService;
import dev.payflow.apigateway.security.InvalidTokenException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Access/refresh token endpoints (BR-11) — the only paths the gateway's JWT filter leaves public. */
@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/token")
  public ResponseEntity<?> token(@RequestBody TokenRequest request) {
    if (!"MERCHANT".equals(request.role()) && !"OPERATOR".equals(request.role())) {
      return ResponseEntity.badRequest().body(Map.of("error", "role must be MERCHANT or OPERATOR"));
    }
    boolean isMerchant = "MERCHANT".equals(request.role());
    UUID merchantId = isMerchant ? (request.merchantId() != null ? request.merchantId() : UUID.randomUUID()) : null;
    return ResponseEntity.ok(authService.login("ROLE_" + request.role(), merchantId));
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(@RequestBody RefreshTokenRequest request) {
    try {
      return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    } catch (InvalidTokenException e) {
      return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
    }
  }

  @PostMapping("/revoke")
  public ResponseEntity<Map<String, String>> revoke(@RequestBody RefreshTokenRequest request) {
    authService.revoke(request.refreshToken());
    return ResponseEntity.ok(Map.of("status", "REVOKED"));
  }
}
