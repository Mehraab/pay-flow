package dev.payflow.notification.delivery;

import java.util.Optional;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Milestone 1 scope: a single synchronous delivery attempt, no retry scheduling yet (that's what
 * {@code webhook_deliveries.next_retry_at} and its partial index exist for, in a later milestone).
 */
@Service
public class WebhookDispatcherService {

  private final WebhookEndpointRepository endpointRepository;
  private final WebhookDeliveryRepository deliveryRepository;
  private final RestClient restClient;

  public WebhookDispatcherService(
      WebhookEndpointRepository endpointRepository,
      WebhookDeliveryRepository deliveryRepository,
      RestClient restClient) {
    this.endpointRepository = endpointRepository;
    this.deliveryRepository = deliveryRepository;
    this.restClient = restClient;
  }

  public void dispatch(UUID merchantId, String eventType, String payload) {
    Optional<WebhookEndpoint> endpoint = endpointRepository.findActiveByMerchantId(merchantId);
    UUID deliveryId =
        deliveryRepository.insertDelivery(
            endpoint.map(WebhookEndpoint::id).orElse(null), eventType, payload);

    if (endpoint.isEmpty()) {
      // No endpoint configured for this merchant yet — nothing to deliver to, so there is
      // nothing left to retry; a mock dispatch is considered immediately delivered.
      deliveryRepository.markDelivered(deliveryId, null);
      return;
    }

    attemptDelivery(deliveryId, endpoint.get(), payload);
  }

  private void attemptDelivery(UUID deliveryId, WebhookEndpoint endpoint, String payload) {
    try {
      var response =
          restClient
              .post()
              .uri(endpoint.url())
              .contentType(MediaType.APPLICATION_JSON)
              .body(payload)
              .retrieve()
              .toBodilessEntity();

      // retrieve() throws on a non-2xx response (handled in the catch block below), so reaching
      // here always means success.
      deliveryRepository.markDelivered(deliveryId, response.getStatusCode().value());
    } catch (RestClientResponseException e) {
      deliveryRepository.markFailed(deliveryId, e.getStatusCode().value());
    } catch (RestClientException e) {
      // No response at all (connection refused, timeout, DNS failure, ...).
      deliveryRepository.markFailed(deliveryId, null);
    }
  }
}
