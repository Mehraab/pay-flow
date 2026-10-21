package dev.payflow.notification.web;

import dev.payflow.notification.delivery.WebhookDeliveryRepository;
import dev.payflow.notification.delivery.WebhookDispatcherService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

  private final WebhookDispatcherService dispatcherService;
  private final WebhookDeliveryRepository deliveryRepository;

  public NotificationController(
      WebhookDispatcherService dispatcherService, WebhookDeliveryRepository deliveryRepository) {
    this.dispatcherService = dispatcherService;
    this.deliveryRepository = deliveryRepository;
  }

  @PostMapping("/internal/webhooks/dispatch")
  public DispatchWebhookResponse dispatch(@RequestBody DispatchWebhookRequest request) {
    dispatcherService.dispatch(request.merchantId(), request.eventType(), request.payload());
    return new DispatchWebhookResponse("ENQUEUED");
  }

  /** BE-08: merchant-facing delivery log — every delivery (success and failure), not just the DLQ. */
  @GetMapping("/webhooks/deliveries")
  public PageResponse<WebhookDeliveryResponse> getMerchantDeliveries(
      @RequestHeader("X-Merchant-Id") UUID merchantId,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    List<WebhookDeliveryResponse> content =
        deliveryRepository.findByMerchantId(merchantId, page * size, size).stream()
            .map(WebhookDeliveryResponse::from)
            .toList();
    long totalElements = deliveryRepository.countByMerchantId(merchantId);
    int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
    return new PageResponse<>(content, page, size, totalElements, totalPages);
  }
}
