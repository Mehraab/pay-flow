package dev.payflow.notification.web;

import dev.payflow.notification.delivery.WebhookDeliveryRepository;
import dev.payflow.notification.delivery.WebhookDispatcherService;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operator-only dead-letter queue inspection and manual retry for webhook deliveries. */
@RestController
@RequestMapping("/operator/webhooks/dlq")
public class WebhookDlqController {

  private final WebhookDeliveryRepository deliveryRepository;
  private final WebhookDispatcherService dispatcherService;

  public WebhookDlqController(
      WebhookDeliveryRepository deliveryRepository, WebhookDispatcherService dispatcherService) {
    this.deliveryRepository = deliveryRepository;
    this.dispatcherService = dispatcherService;
  }

  @GetMapping
  public List<WebhookDeliveryResponse> deadLetterDeliveries() {
    return deliveryRepository.findDeadLetter().stream().map(WebhookDeliveryResponse::from).toList();
  }

  @PostMapping("/{id}/retry")
  public ResponseEntity<WebhookDeliveryResponse> retry(@PathVariable UUID id) {
    try {
      dispatcherService.retryDelivery(id);
    } catch (NoSuchElementException e) {
      return ResponseEntity.notFound().build();
    }
    return deliveryRepository
        .findById(id)
        .map(delivery -> ResponseEntity.ok(WebhookDeliveryResponse.from(delivery)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
