package dev.payflow.ledger.web;

import dev.payflow.ledger.payout.Payout;
import dev.payflow.ledger.posting.LedgerPostingService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PayoutController {

  private final LedgerPostingService postingService;

  public PayoutController(LedgerPostingService postingService) {
    this.postingService = postingService;
  }

  @PostMapping("/payouts")
  public ResponseEntity<PayoutResponse> createPayout(@RequestBody CreatePayoutRequest request) {
    Payout payout = postingService.createPayout(request.merchantId());
    return ResponseEntity.status(201).body(toResponse(payout));
  }

  @GetMapping("/payouts/{id}")
  public ResponseEntity<PayoutResponse> getPayout(@PathVariable UUID id) {
    return postingService.findPayoutById(id).map(this::toResponse).map(ResponseEntity::ok).orElseGet(
        () -> ResponseEntity.notFound().build());
  }

  @PostMapping("/payouts/sweep")
  public ResponseEntity<List<PayoutResponse>> sweep() {
    List<Payout> payouts = postingService.sweepAllEligibleMerchants();
    return ResponseEntity.ok(payouts.stream().map(this::toResponse).toList());
  }

  private PayoutResponse toResponse(Payout payout) {
    return new PayoutResponse(
        payout.id(), payout.merchantId(), payout.amountMinor(), payout.currency(), payout.status().name(),
        payout.createdAt());
  }
}
