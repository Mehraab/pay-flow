package dev.payflow.paymentquery.web;

import dev.payflow.paymentquery.view.PaymentViewStore;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentQueryController {

  private final PaymentViewStore store;

  public PaymentQueryController(PaymentViewStore store) {
    this.store = store;
  }

  @GetMapping("/payments/{id}")
  public ResponseEntity<?> getPayment(@PathVariable UUID id) {
    return store.findById(id).<ResponseEntity<?>>map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
  }
}
