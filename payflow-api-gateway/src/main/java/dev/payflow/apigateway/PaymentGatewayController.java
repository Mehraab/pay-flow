package dev.payflow.apigateway;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;

/**
 * Pass-through routing to the two downstream services (ARCHITECTURE_AND_PLAN.md service
 * topology) — not a general-purpose reverse proxy, just the two routes Milestone 1 needs:
 * {@code POST /api/v1/payments} to the Payment Command Service, {@code GET
 * /api/v1/payments/{id}} to the Payment Query Service. Status code and body are relayed
 * unchanged either way.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentGatewayController {

  private final RestClient restClient;
  private final String paymentCommandUrl;
  private final String paymentQueryUrl;

  public PaymentGatewayController(
      RestClient restClient,
      @Value("${payflow.gateway.payment-command-url}") String paymentCommandUrl,
      @Value("${payflow.gateway.payment-query-url}") String paymentQueryUrl) {
    this.restClient = restClient;
    this.paymentCommandUrl = paymentCommandUrl;
    this.paymentQueryUrl = paymentQueryUrl;
  }

  @PostMapping
  public ResponseEntity<byte[]> createPayment(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody byte[] body) {
    var request =
        restClient.post().uri(paymentCommandUrl + "/payments").contentType(MediaType.APPLICATION_JSON);
    if (idempotencyKey != null) {
      request = request.header("Idempotency-Key", idempotencyKey);
    }
    return request.body(body).exchange(this::relay);
  }

  @GetMapping("/{id}")
  public ResponseEntity<byte[]> getPayment(@PathVariable String id) {
    return restClient.get().uri(paymentQueryUrl + "/payments/{id}", id).exchange(this::relay);
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
