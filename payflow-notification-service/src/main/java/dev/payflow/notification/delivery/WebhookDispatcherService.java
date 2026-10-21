package dev.payflow.notification.delivery;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * A failed delivery is retried with backoff rather than given up on immediately — {@code
 * webhook_deliveries.next_retry_at} and its partial index exist for a scheduled poller to pick
 * up due retries in a later milestone; this milestone only computes the schedule and exposes a
 * manual operator retry ({@link #retryDelivery}).
 */
@Service
public class WebhookDispatcherService {

  private static final int MAX_ATTEMPTS = 5;
  private static final Map<Integer, Duration> BACKOFF_SCHEDULE =
      Map.of(
          1, Duration.ofSeconds(1),
          2, Duration.ofSeconds(5),
          3, Duration.ofSeconds(30),
          4, Duration.ofMinutes(2),
          5, Duration.ofMinutes(10));

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

  /** Resets a dead-lettered (or otherwise stuck) delivery and immediately re-attempts it. */
  public void retryDelivery(UUID deliveryId) {
    WebhookDelivery delivery =
        deliveryRepository
            .findById(deliveryId)
            .orElseThrow(() -> new NoSuchElementException("No webhook delivery with id '" + deliveryId + "'"));
    // DEAD_LETTER is only ever reached via attemptDelivery's failure path below, which only
    // runs once an endpoint was found — so endpointId is never null here.
    WebhookEndpoint endpoint =
        endpointRepository
            .findById(delivery.endpointId())
            .orElseThrow(
                () -> new NoSuchElementException("No webhook endpoint with id '" + delivery.endpointId() + "'"));

    deliveryRepository.resetForRetry(deliveryId);
    attemptDelivery(deliveryId, endpoint, delivery.payload());
  }

  void attemptDelivery(UUID deliveryId, WebhookEndpoint endpoint, String payload) {
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
      recordFailure(deliveryId, e.getStatusCode().value());
    } catch (RestClientException e) {
      // No response at all (connection refused, timeout, DNS failure, ...).
      recordFailure(deliveryId, null);
    }
  }

  private void recordFailure(UUID deliveryId, Integer responseCode) {
    int attemptCount = deliveryRepository.recordAttemptFailure(deliveryId, responseCode);
    if (attemptCount >= MAX_ATTEMPTS) {
      deliveryRepository.markDeadLetter(deliveryId);
    } else {
      deliveryRepository.scheduleRetry(deliveryId, Instant.now().plus(backoffFor(attemptCount)));
    }
  }

  static Duration backoffFor(int attemptCount) {
    return BACKOFF_SCHEDULE.getOrDefault(attemptCount, Duration.ofMinutes(10));
  }
}
