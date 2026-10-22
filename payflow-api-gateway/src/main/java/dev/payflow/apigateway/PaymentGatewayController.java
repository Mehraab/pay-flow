package dev.payflow.apigateway;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Pass-through routing to the downstream services (ARCHITECTURE_AND_PLAN.md service topology) —
 * not a general-purpose reverse proxy, just the routes the merchant-facing gateway needs: payment
 * CRUD/refund to the Payment Command/Query Services, plus the operator-only ledger-integrity and
 * provider-routing routes. Status code and body are relayed unchanged either way.
 */
@RestController
public class PaymentGatewayController {

  private final RestClient restClient;
  private final String paymentCommandUrl;
  private final String paymentQueryUrl;
  private final String ledgerUrl;
  private final String providerGatewayUrl;
  private final String orchestratorUrl;
  private final String notificationUrl;

  public PaymentGatewayController(
      RestClient restClient,
      @Value("${payflow.gateway.payment-command-url}") String paymentCommandUrl,
      @Value("${payflow.gateway.payment-query-url}") String paymentQueryUrl,
      @Value("${payflow.gateway.ledger-url}") String ledgerUrl,
      @Value("${payflow.gateway.provider-gateway-url}") String providerGatewayUrl,
      @Value("${payflow.gateway.orchestrator-url}") String orchestratorUrl,
      @Value("${payflow.gateway.notification-url}") String notificationUrl) {
    this.restClient = restClient;
    this.paymentCommandUrl = paymentCommandUrl;
    this.paymentQueryUrl = paymentQueryUrl;
    this.ledgerUrl = ledgerUrl;
    this.providerGatewayUrl = providerGatewayUrl;
    this.orchestratorUrl = orchestratorUrl;
    this.notificationUrl = notificationUrl;
  }

  @PostMapping("/api/v1/payments")
  public ResponseEntity<byte[]> createPayment(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestHeader(value = "X-Force-Ledger-Failure", required = false) String forceLedgerFailure,
      @RequestBody byte[] body) {
    var request =
        restClient.post().uri(paymentCommandUrl + "/payments").contentType(MediaType.APPLICATION_JSON);
    if (idempotencyKey != null) {
      request = request.header("Idempotency-Key", idempotencyKey);
    }
    if (forceLedgerFailure != null) {
      request = request.header("X-Force-Ledger-Failure", forceLedgerFailure);
    }
    return request.body(body).exchange(this::relay);
  }

  // Declared ahead of GET /api/v1/payments/{id} — Spring MVC must never mistake "summary" for a
  // payment id.
  @GetMapping("/api/v1/payments/summary")
  public ResponseEntity<byte[]> paymentsSummary(
      @RequestHeader(value = "X-Merchant-Id", required = false) String merchantId,
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam(value = "days", defaultValue = "30") int days) {
    URI uri =
        UriComponentsBuilder.fromUriString(paymentQueryUrl + "/payments/summary").queryParam("days", days).build().toUri();
    RequestHeadersSpec<?> spec = restClient.get().uri(uri);
    if (merchantId != null) {
      spec = spec.header("X-Merchant-Id", merchantId);
    }
    if (authorization != null) {
      spec = spec.header("Authorization", authorization);
    }
    return spec.exchange(this::relay);
  }

  @GetMapping("/api/v1/payments/{id}")
  public ResponseEntity<byte[]> getPayment(@PathVariable String id) {
    return restClient.get().uri(paymentQueryUrl + "/payments/{id}", id).exchange(this::relay);
  }

  @PostMapping("/api/v1/payments/{id}/refund")
  public ResponseEntity<byte[]> refundPayment(@PathVariable String id) {
    return restClient.post().uri(paymentCommandUrl + "/payments/{id}/refund", id).exchange(this::relay);
  }

  @GetMapping("/api/v1/payments/{id}/events")
  public ResponseEntity<byte[]> paymentTimeline(@PathVariable String id) {
    return restClient.get().uri(paymentQueryUrl + "/payments/{id}/events", id).exchange(this::relay);
  }

  @GetMapping("/api/v1/payments")
  public ResponseEntity<byte[]> listPayments(
      @RequestHeader(value = "X-Merchant-Id", required = false) String merchantId,
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "q", required = false) String q,
      @RequestParam(value = "page", required = false) Integer page,
      @RequestParam(value = "size", required = false) Integer size) {
    URI uri =
        UriComponentsBuilder.fromUriString(paymentQueryUrl + "/payments")
            .queryParamIfPresent("status", Optional.ofNullable(status))
            .queryParamIfPresent("q", Optional.ofNullable(q))
            .queryParamIfPresent("page", Optional.ofNullable(page))
            .queryParamIfPresent("size", Optional.ofNullable(size))
            .build()
            .toUri();
    RequestHeadersSpec<?> spec = restClient.get().uri(uri);
    if (merchantId != null) {
      spec = spec.header("X-Merchant-Id", merchantId);
    }
    if (authorization != null) {
      spec = spec.header("Authorization", authorization);
    }
    return spec.exchange(this::relay);
  }

  @GetMapping("/operator/ledger/integrity")
  public ResponseEntity<byte[]> ledgerIntegrity() {
    return restClient.get().uri(ledgerUrl + "/operator/ledger/integrity").exchange(this::relay);
  }

  @GetMapping("/accounts/{accountName}/balance")
  public ResponseEntity<byte[]> platformAccountBalance(@PathVariable String accountName) {
    return restClient.get().uri(ledgerUrl + "/accounts/{accountName}/balance", accountName).exchange(this::relay);
  }

  @GetMapping("/accounts/{merchantId}/{accountName}/balance")
  public ResponseEntity<byte[]> merchantAccountBalance(
      @PathVariable String merchantId, @PathVariable String accountName) {
    return restClient
        .get()
        .uri(ledgerUrl + "/accounts/{merchantId}/{accountName}/balance", merchantId, accountName)
        .exchange(this::relay);
  }

  @GetMapping("/ledger")
  public ResponseEntity<byte[]> ledgerEntries(@RequestParam("transaction") String transactionRef) {
    URI uri = UriComponentsBuilder.fromUriString(ledgerUrl + "/ledger").queryParam("transaction", transactionRef).build().toUri();
    return restClient.get().uri(uri).exchange(this::relay);
  }

  @GetMapping("/ledger/merchants/{merchantId}/entries")
  public ResponseEntity<byte[]> merchantLedgerEntries(
      @PathVariable String merchantId,
      @RequestHeader(value = "X-Merchant-Id", required = false) String merchantIdHeader,
      @RequestParam(value = "page", required = false) Integer page,
      @RequestParam(value = "size", required = false) Integer size) {
    URI uri =
        UriComponentsBuilder.fromUriString(ledgerUrl + "/ledger/merchants/" + merchantId + "/entries")
            .queryParamIfPresent("page", Optional.ofNullable(page))
            .queryParamIfPresent("size", Optional.ofNullable(size))
            .build()
            .toUri();
    RequestHeadersSpec<?> spec = restClient.get().uri(uri);
    if (merchantIdHeader != null) {
      spec = spec.header("X-Merchant-Id", merchantIdHeader);
    }
    return spec.exchange(this::relay);
  }

  @PostMapping("/payouts")
  public ResponseEntity<byte[]> createPayout(@RequestBody byte[] body) {
    return restClient.post().uri(ledgerUrl + "/payouts").contentType(MediaType.APPLICATION_JSON).body(body).exchange(this::relay);
  }

  // Declared ahead of GET /payouts/{id} — Spring MVC must never mistake "merchants" for a payout
  // id.
  @GetMapping("/payouts")
  public ResponseEntity<byte[]> listPayouts(
      @RequestHeader(value = "X-Merchant-Id", required = false) String merchantIdHeader,
      @RequestParam(value = "merchantId", required = false) String merchantId,
      @RequestParam(value = "page", required = false) Integer page,
      @RequestParam(value = "size", required = false) Integer size) {
    URI uri =
        UriComponentsBuilder.fromUriString(ledgerUrl + "/payouts")
            .queryParamIfPresent("merchantId", Optional.ofNullable(merchantId))
            .queryParamIfPresent("page", Optional.ofNullable(page))
            .queryParamIfPresent("size", Optional.ofNullable(size))
            .build()
            .toUri();
    RequestHeadersSpec<?> spec = restClient.get().uri(uri);
    if (merchantIdHeader != null) {
      spec = spec.header("X-Merchant-Id", merchantIdHeader);
    }
    return spec.exchange(this::relay);
  }

  @GetMapping("/payouts/merchants/{merchantId}")
  public ResponseEntity<byte[]> payoutsForMerchant(
      @PathVariable String merchantId,
      @RequestHeader(value = "X-Merchant-Id", required = false) String merchantIdHeader,
      @RequestParam(value = "page", required = false) Integer page,
      @RequestParam(value = "size", required = false) Integer size) {
    URI uri =
        UriComponentsBuilder.fromUriString(ledgerUrl + "/payouts/merchants/" + merchantId)
            .queryParamIfPresent("page", Optional.ofNullable(page))
            .queryParamIfPresent("size", Optional.ofNullable(size))
            .build()
            .toUri();
    RequestHeadersSpec<?> spec = restClient.get().uri(uri);
    if (merchantIdHeader != null) {
      spec = spec.header("X-Merchant-Id", merchantIdHeader);
    }
    return spec.exchange(this::relay);
  }

  @GetMapping("/payouts/{id}")
  public ResponseEntity<byte[]> getPayout(@PathVariable String id) {
    return restClient.get().uri(ledgerUrl + "/payouts/{id}", id).exchange(this::relay);
  }

  @PostMapping("/operator/payouts/sweep")
  public ResponseEntity<byte[]> sweepPayouts() {
    return restClient.post().uri(ledgerUrl + "/payouts/sweep").exchange(this::relay);
  }

  @GetMapping("/operator/ledger/entries")
  public ResponseEntity<byte[]> ledgerEntriesFeed(
      @RequestParam(value = "since", required = false) String since,
      @RequestParam(value = "page", required = false) Integer page,
      @RequestParam(value = "size", required = false) Integer size) {
    URI uri =
        UriComponentsBuilder.fromUriString(ledgerUrl + "/operator/ledger/entries")
            .queryParamIfPresent("since", Optional.ofNullable(since))
            .queryParamIfPresent("page", Optional.ofNullable(page))
            .queryParamIfPresent("size", Optional.ofNullable(size))
            .build()
            .toUri();
    return restClient.get().uri(uri).exchange(this::relay);
  }

  @GetMapping("/webhooks/deliveries")
  public ResponseEntity<byte[]> webhookDeliveries(
      @RequestHeader(value = "X-Merchant-Id", required = false) String merchantId,
      @RequestParam(value = "page", required = false) Integer page,
      @RequestParam(value = "size", required = false) Integer size) {
    URI uri =
        UriComponentsBuilder.fromUriString(notificationUrl + "/webhooks/deliveries")
            .queryParamIfPresent("page", Optional.ofNullable(page))
            .queryParamIfPresent("size", Optional.ofNullable(size))
            .build()
            .toUri();
    RequestHeadersSpec<?> spec = restClient.get().uri(uri);
    if (merchantId != null) {
      spec = spec.header("X-Merchant-Id", merchantId);
    }
    return spec.exchange(this::relay);
  }

  @RequestMapping(value = "/operator/providers/**", method = {RequestMethod.GET, RequestMethod.PATCH})
  public ResponseEntity<byte[]> proxyProviderRouting(
      HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    RestClient.RequestBodySpec requestSpec =
        restClient.method(HttpMethod.valueOf(request.getMethod())).uri(providerGatewayUrl + request.getRequestURI());
    RequestHeadersSpec<?> spec =
        (body != null && body.length > 0)
            ? requestSpec.contentType(MediaType.APPLICATION_JSON).body(body)
            : requestSpec;
    return spec.exchange(this::relay);
  }

  @GetMapping("/operator/saga-events")
  public ResponseEntity<byte[]> sagaEvents() {
    return restClient.get().uri(orchestratorUrl + "/operator/saga-events").exchange(this::relay);
  }

  @RequestMapping(value = "/operator/webhooks/dlq/**", method = {RequestMethod.GET, RequestMethod.POST})
  public ResponseEntity<byte[]> proxyWebhookDlq(
      HttpServletRequest request, @RequestBody(required = false) byte[] body) {
    RestClient.RequestBodySpec requestSpec =
        restClient.method(HttpMethod.valueOf(request.getMethod())).uri(notificationUrl + request.getRequestURI());
    RequestHeadersSpec<?> spec =
        (body != null && body.length > 0)
            ? requestSpec.contentType(MediaType.APPLICATION_JSON).body(body)
            : requestSpec;
    return spec.exchange(this::relay);
  }

  private ResponseEntity<byte[]> relay(HttpRequest request, ConvertibleClientHttpResponse response)
      throws IOException {
    byte[] responseBody = response.getBody().readAllBytes();
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.getStatusCode());
    MediaType contentType = response.getHeaders().getContentType();
    if (contentType != null) {
      builder = builder.contentType(contentType);
    }
    return builder.body(responseBody);
  }
}
