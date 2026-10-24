package dev.payflow.apigateway.web;

import dev.payflow.apigateway.security.AuthService;
import dev.payflow.apigateway.security.InvalidTokenException;
import dev.payflow.common.dto.ErrorResponse;
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
      return ResponseEntity.badRequest().body(ErrorResponse.of("role must be MERCHANT or OPERATOR", 400, "/auth/token"));
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
      return ResponseEntity.status(401).body(ErrorResponse.of(e.getMessage(), 401, "/auth/refresh"));
    }
  }

  @PostMapping("/revoke")
  public ResponseEntity<Map<String, String>> revoke(@RequestBody RefreshTokenRequest request) {
    authService.revoke(request.refreshToken());
    return ResponseEntity.ok(Map.of("status", "REVOKED"));
  }
}
