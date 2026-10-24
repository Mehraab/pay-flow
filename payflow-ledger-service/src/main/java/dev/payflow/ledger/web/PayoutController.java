package dev.payflow.ledger.web;

import dev.payflow.common.dto.ErrorResponse;
import dev.payflow.ledger.payout.Payout;
import dev.payflow.ledger.posting.LedgerPostingService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
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

  // Declared ahead of GET /payouts/{id} — Spring MVC must never mistake "merchants" for a payout id.
  @GetMapping("/payouts")
  public ResponseEntity<?> listPayouts(
      @RequestHeader(value = "X-Merchant-Id", required = false) String merchantIdHeader,
      @RequestParam(value = "merchantId", required = false) UUID queryMerchantId,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "20") int size,
      HttpServletRequest request) {
    UUID targetMerchantId =
        queryMerchantId != null ? queryMerchantId : (merchantIdHeader != null ? UUID.fromString(merchantIdHeader) : null);
    if (targetMerchantId == null) {
      return ResponseEntity.badRequest()
          .body(
              ErrorResponse.of(
                  "merchantId is required (via the 'merchantId' query param or the X-Merchant-Id header)",
                  400,
                  request.getRequestURI()));
    }
    if (merchantIdHeader != null && !targetMerchantId.toString().equalsIgnoreCase(merchantIdHeader)) {
      return ResponseEntity.status(403)
          .body(
              ErrorResponse.of(
                  "X-Merchant-Id header does not match the requested merchantId", 403, request.getRequestURI()));
    }
    List<PayoutResponse> content =
        postingService.findMerchantPayoutsPaged(targetMerchantId, page, size).stream().map(this::toResponse).toList();
    long totalElements = postingService.countMerchantPayouts(targetMerchantId);
    int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
    return ResponseEntity.ok(new PageResponse<>(content, page, size, totalElements, totalPages));
  }

  @GetMapping("/payouts/merchants/{merchantId}")
  public ResponseEntity<?> listPayoutsForMerchant(
      @RequestHeader(value = "X-Merchant-Id", required = false) String merchantIdHeader,
      @PathVariable UUID merchantId,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "20") int size,
      HttpServletRequest request) {
    return listPayouts(merchantIdHeader, merchantId, page, size, request);
  }

  @GetMapping("/payouts/{id}")
  public ResponseEntity<?> getPayout(@PathVariable UUID id, HttpServletRequest request) {
    return postingService
        .findPayoutById(id)
        .<ResponseEntity<?>>map(payout -> ResponseEntity.ok(toResponse(payout)))
        .orElseGet(
            () ->
                ResponseEntity.status(404)
                    .body(ErrorResponse.of("Payout " + id + " not found", 404, request.getRequestURI())));
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
