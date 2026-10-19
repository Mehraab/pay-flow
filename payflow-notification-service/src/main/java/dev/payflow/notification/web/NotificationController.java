package dev.payflow.notification.web;

import dev.payflow.notification.delivery.WebhookDispatcherService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

  private final WebhookDispatcherService dispatcherService;

  public NotificationController(WebhookDispatcherService dispatcherService) {
    this.dispatcherService = dispatcherService;
  }

  @PostMapping("/internal/webhooks/dispatch")
  public DispatchWebhookResponse dispatch(@RequestBody DispatchWebhookRequest request) {
    dispatcherService.dispatch(request.merchantId(), request.eventType(), request.payload());
    return new DispatchWebhookResponse("ENQUEUED");
  }
}
