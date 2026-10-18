package dev.payflow.providergateway;

import dev.payflow.common.dto.AuthorizePaymentRequest;
import dev.payflow.common.dto.AuthorizePaymentResponse;
import dev.payflow.common.dto.VoidChargeRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal-only endpoints called by Temporal Activities (ARCHITECTURE_AND_PLAN.md §1.2,
 * {@code authorizeAndCapture} and {@code reverseProviderCharge}) — never by the merchant-facing
 * API Gateway directly.
 */
@RestController
public class ProviderGatewayController {

  private final MockProviderGateway providerGateway;

  public ProviderGatewayController(MockProviderGateway providerGateway) {
    this.providerGateway = providerGateway;
  }

  @PostMapping("/internal/providers/charge")
  public ResponseEntity<AuthorizePaymentResponse> charge(
      @RequestBody AuthorizePaymentRequest request) {
    return ResponseEntity.ok(providerGateway.charge(request));
  }

  @PostMapping("/internal/providers/void")
  public ResponseEntity<Void> voidCharge(@RequestBody VoidChargeRequest request) {
    providerGateway.voidCharge(request.providerRef());
    return ResponseEntity.ok().build();
  }
}
