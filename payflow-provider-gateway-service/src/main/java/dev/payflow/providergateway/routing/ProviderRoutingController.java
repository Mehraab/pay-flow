package dev.payflow.providergateway.routing;

import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Operator-only endpoints for inspecting and adjusting BR-13 dynamic provider routing. */
@RestController
public class ProviderRoutingController {

  private final ProviderRoutingService routingService;

  public ProviderRoutingController(ProviderRoutingService routingService) {
    this.routingService = routingService;
  }

  @GetMapping("/operator/providers")
  public List<ProviderConfig> listProviders() {
    return routingService.listProviders();
  }

  @PatchMapping("/operator/providers/{id}/routing")
  public ResponseEntity<ProviderConfig> updateRouting(
      @PathVariable String id, @RequestBody UpdateRoutingRequest request) {
    try {
      return ResponseEntity.ok(routingService.updateRouting(id, request));
    } catch (NoSuchElementException e) {
      return ResponseEntity.notFound().build();
    }
  }
}
