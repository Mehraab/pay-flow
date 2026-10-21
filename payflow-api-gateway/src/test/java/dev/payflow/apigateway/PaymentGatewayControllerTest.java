package dev.payflow.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

/**
 * Verifies the gateway actually forwards to the two downstream services, using plain JDK {@link
 * HttpServer} stand-ins bound to the real payment-command (8081) and payment-query (8082) ports
 * — the same ports {@code application.yml} points at — rather than mocking the forwarding layer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentGatewayControllerTest {

  @LocalServerPort private int port;

  private final TestRestTemplate restTemplate = new TestRestTemplate();

  private HttpServer commandBackend;
  private HttpServer queryBackend;
  private volatile String lastCommandIdempotencyKey;
  private volatile String lastCommandBody;
  private volatile String lastCommandPath;
  private volatile String lastCommandMethod;

  @BeforeEach
  void startBackends() throws IOException {
    commandBackend = HttpServer.create(new InetSocketAddress("localhost", 8081), 0);
    commandBackend.createContext(
        "/payments",
        exchange -> {
          lastCommandPath = exchange.getRequestURI().getPath();
          lastCommandMethod = exchange.getRequestMethod();
          if (lastCommandPath.endsWith("/refund")) {
            byte[] body = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            return;
          }
          lastCommandIdempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");
          lastCommandBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          byte[] body = "{\"paymentId\":\"stub-id\"}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(201, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    commandBackend.start();

    queryBackend = HttpServer.create(new InetSocketAddress("localhost", 8082), 0);
    queryBackend.createContext(
        "/payments",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          byte[] body;
          int status;
          if (path.endsWith("/missing")) {
            status = 404;
            body = new byte[0];
          } else {
            status = 200;
            body = "{\"status\":\"CAPTURED\"}".getBytes(StandardCharsets.UTF_8);
          }
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    queryBackend.start();
  }

  @AfterEach
  void stopBackends() {
    commandBackend.stop(0);
    queryBackend.stop(0);
  }

  @Test
  void postPaymentsForwardsBodyAndIdempotencyKeyToThePaymentCommandServiceAndRelaysTheResponse() {
    RequestEntity<String> request =
        RequestEntity.post(URI.create("http://localhost:" + port + "/api/v1/payments"))
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", "demo-001")
            .body("{\"merchantId\":\"m-1\"}");

    ResponseEntity<String> response = restTemplate.exchange(request, String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getBody()).contains("stub-id");
    assertThat(lastCommandIdempotencyKey).isEqualTo("demo-001");
    assertThat(lastCommandBody).isEqualTo("{\"merchantId\":\"m-1\"}");
  }

  @Test
  void getPaymentsForwardsToThePaymentQueryServiceAndRelaysTheResponse() {
    ResponseEntity<String> response =
        restTemplate.getForEntity(
            "http://localhost:" + port + "/api/v1/payments/{id}", String.class, "abc-123");

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).contains("CAPTURED");
  }

  @Test
  void getPaymentsRelaysANotFoundStatusUnchanged() {
    ResponseEntity<String> response =
        restTemplate.getForEntity(
            "http://localhost:" + port + "/api/v1/payments/{id}", String.class, "missing");

    assertThat(response.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void postRefundForwardsToThePaymentCommandServiceRefundRouteAndRelaysTheResponse() {
    ResponseEntity<String> response =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v1/payments/{id}/refund", null, String.class, "abc-123");

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(lastCommandMethod).isEqualTo("POST");
    assertThat(lastCommandPath).isEqualTo("/payments/abc-123/refund");
  }
}
