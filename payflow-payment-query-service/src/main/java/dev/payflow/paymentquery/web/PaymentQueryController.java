package dev.payflow.paymentquery.web;

import dev.payflow.common.dto.ErrorResponse;
import dev.payflow.paymentquery.view.PaymentView;
import dev.payflow.paymentquery.view.PaymentViewStore;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentQueryController {

  private final PaymentViewStore store;

  public PaymentQueryController(PaymentViewStore store) {
    this.store = store;
  }

  @GetMapping("/payments")
  public PageResponse<PaymentView> listPayments(
      @RequestHeader(value = "X-Merchant-Id", required = false) String merchantIdHeader,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "q", required = false) String q,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "10") int size) {
    UUID merchantId = merchantIdHeader != null ? UUID.fromString(merchantIdHeader) : null;
    return store.findPayments(merchantId, status, q, page, size);
  }

  // Declared ahead of GET /payments/{id} — "summary" must never be mistaken for a payment id.
  @GetMapping("/payments/summary")
  public PaymentSummaryResponse getSummary(
      @RequestHeader(value = "X-Merchant-Id", required = false) String merchantIdHeader,
      @RequestParam(value = "days", defaultValue = "30") int days) {
    UUID merchantId = merchantIdHeader != null ? UUID.fromString(merchantIdHeader) : null;
    return store.getSummary(merchantId, days);
  }

  @GetMapping("/payments/{id}")
  public ResponseEntity<?> getPayment(@PathVariable UUID id) {
    return store
        .findById(id)
        .<ResponseEntity<?>>map(ResponseEntity::ok)
        .orElseGet(
            () ->
                ResponseEntity.status(404)
                    .body(ErrorResponse.of("Payment " + id + " not found", 404, "/payments/" + id)));
  }

  @GetMapping("/payments/{id}/events")
  public ResponseEntity<?> getTimeline(@PathVariable UUID id) {
    return store
        .findTimeline(id)
        .<ResponseEntity<?>>map(ResponseEntity::ok)
        .orElseGet(
            () ->
                ResponseEntity.status(404)
                    .body(
                        ErrorResponse.of(
                            "Timeline for payment " + id + " not found", 404, "/payments/" + id + "/events")));
  }
}
