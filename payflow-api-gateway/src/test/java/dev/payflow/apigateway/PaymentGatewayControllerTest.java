package dev.payflow.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.payflow.apigateway.security.JwtTokenService;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

/**
 * Verifies the gateway actually forwards to the two downstream services, using plain JDK {@link
 * HttpServer} stand-ins bound to the real payment-command (8081) and payment-query (8082) ports
 * — the same ports {@code application.yml} points at — rather than mocking the forwarding layer.
 * Requests carry a real access token minted directly from {@link JwtTokenService} (not via {@code
 * /auth/token}, which would need a live Redis) so they clear the gateway's JWT filter.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentGatewayControllerTest {

  @LocalServerPort private int port;
  @Autowired private JwtTokenService jwtTokenService;

  private final TestRestTemplate restTemplate = new TestRestTemplate();

  private String merchantAuthHeader() {
    return "Bearer " + jwtTokenService.issueAccessToken(UUID.randomUUID().toString(), "ROLE_MERCHANT", UUID.randomUUID());
  }

  private HttpServer commandBackend;
  private HttpServer queryBackend;
  private HttpServer ledgerBackend;
  private HttpServer notificationBackend;
  private volatile String lastCommandIdempotencyKey;
  private volatile String lastCommandBody;
  private volatile String lastCommandPath;
  private volatile String lastCommandMethod;
  private volatile String lastQueryPath;
  private volatile String lastQueryQuery;
  private volatile String lastQueryMerchantIdHeader;
  private volatile String lastQueryAuthorizationHeader;
  private volatile String lastLedgerPath;
  private volatile String lastLedgerQuery;
  private volatile String lastLedgerMethod;
  private volatile String lastLedgerBody;
  private volatile String lastNotificationPath;
  private volatile String lastNotificationQuery;
  private volatile String lastNotificationMerchantIdHeader;

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
          if (path.equals("/payments")) {
            lastQueryPath = path;
            lastQueryQuery = exchange.getRequestURI().getQuery();
            lastQueryMerchantIdHeader = exchange.getRequestHeaders().getFirst("X-Merchant-Id");
            lastQueryAuthorizationHeader = exchange.getRequestHeaders().getFirst("Authorization");
            status = 200;
            body = "{\"content\":[],\"page\":0,\"size\":10,\"totalElements\":0,\"totalPages\":0}".getBytes(StandardCharsets.UTF_8);
          } else if (path.equals("/payments/summary")) {
            lastQueryPath = path;
            lastQueryQuery = exchange.getRequestURI().getQuery();
            lastQueryMerchantIdHeader = exchange.getRequestHeaders().getFirst("X-Merchant-Id");
            lastQueryAuthorizationHeader = exchange.getRequestHeaders().getFirst("Authorization");
            status = 200;
            body =
                "{\"netVolumeMinor\":0,\"currency\":\"USD\",\"volumeDeltaPct\":0.0,\"successRatePct\":100.0,\"avgAuthSeconds\":0.35,\"dailyVolume\":[]}"
                    .getBytes(StandardCharsets.UTF_8);
          } else if (path.endsWith("/events")) {
            lastQueryPath = path;
            status = 200;
            body = "[]".getBytes(StandardCharsets.UTF_8);
          } else if (path.endsWith("/missing")) {
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

    ledgerBackend = HttpServer.create(new InetSocketAddress("localhost", 8085), 0);
    HttpHandler ledgerHandler =
        exchange -> {
          lastLedgerPath = exchange.getRequestURI().getPath();
          lastLedgerQuery = exchange.getRequestURI().getQuery();
          lastLedgerMethod = exchange.getRequestMethod();
          lastLedgerBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          byte[] body = "{\"stub\":true}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        };
    ledgerBackend.createContext("/accounts", ledgerHandler);
    ledgerBackend.createContext("/ledger", ledgerHandler);
    ledgerBackend.createContext("/payouts", ledgerHandler);
    ledgerBackend.createContext("/operator", ledgerHandler);
    ledgerBackend.start();

    notificationBackend = HttpServer.create(new InetSocketAddress("localhost", 8086), 0);
    notificationBackend.createContext(
        "/webhooks/deliveries",
        exchange -> {
          lastNotificationPath = exchange.getRequestURI().getPath();
          lastNotificationQuery = exchange.getRequestURI().getQuery();
          lastNotificationMerchantIdHeader = exchange.getRequestHeaders().getFirst("X-Merchant-Id");
          byte[] body = "{\"content\":[],\"page\":0,\"size\":20,\"totalElements\":0,\"totalPages\":0}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    notificationBackend.start();
  }

  @AfterEach
  void stopBackends() {
    commandBackend.stop(0);
    queryBackend.stop(0);
    ledgerBackend.stop(0);
    notificationBackend.stop(0);
  }

  @Test
  void postPaymentsForwardsBodyAndIdempotencyKeyToThePaymentCommandServiceAndRelaysTheResponse() {
    RequestEntity<String> request =
        RequestEntity.post(URI.create("http://localhost:" + port + "/api/v1/payments"))
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", "demo-001")
            .header("Authorization", merchantAuthHeader())
            .body("{\"merchantId\":\"m-1\"}");

    ResponseEntity<String> response = restTemplate.exchange(request, String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getBody()).contains("stub-id");
    assertThat(lastCommandIdempotencyKey).isEqualTo("demo-001");
    assertThat(lastCommandBody).isEqualTo("{\"merchantId\":\"m-1\"}");
  }

  @Test
  void getPaymentsForwardsToThePaymentQueryServiceAndRelaysTheResponse() {
    RequestEntity<Void> request =
        RequestEntity.get(URI.create("http://localhost:" + port + "/api/v1/payments/abc-123"))
            .header("Authorization", merchantAuthHeader())
            .build();

    ResponseEntity<String> response = restTemplate.exchange(request, String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).contains("CAPTURED");
  }

  @Test
  void getPaymentsRelaysANotFoundStatusUnchanged() {
    RequestEntity<Void> request =
        RequestEntity.get(URI.create("http://localhost:" + port + "/api/v1/payments/missing"))
            .header("Authorization", merchantAuthHeader())
            .build();

    ResponseEntity<String> response = restTemplate.exchange(request, String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void postRefundForwardsToThePaymentCommandServiceRefundRouteAndRelaysTheResponse() {
    RequestEntity<Void> request =
        RequestEntity.post(URI.create("http://localhost:" + port + "/api/v1/payments/abc-123/refund"))
            .header("Authorization", merchantAuthHeader())
            .build();

    ResponseEntity<String> response = restTemplate.exchange(request, String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(lastCommandMethod).isEqualTo("POST");
    assertThat(lastCommandPath).isEqualTo("/payments/abc-123/refund");
  }

  @Test
  void optionsPreflightIsAnsweredWithCorsHeadersAndNeverReachesTheJwtFilter() {
    RequestEntity<Void> request =
        RequestEntity.options(URI.create("http://localhost:" + port + "/api/v1/payments"))
            .header("Origin", "http://localhost:4200")
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "Authorization")
            .build();

    ResponseEntity<Void> response = restTemplate.exchange(request, Void.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getHeaders().getFirst("Access-Control-Allow-Origin")).isEqualTo("http://localhost:4200");
    assertThat(response.getHeaders().getFirst("Access-Control-Allow-Credentials")).isEqualTo("true");
    assertThat(response.getHeaders().getFirst("Access-Control-Allow-Methods")).contains("GET");
  }

  @Test
  void listPaymentsForwardsQueryParamsAndHeadersToThePaymentQueryService() {
    // The JWT filter derives X-Merchant-Id from the token's own merchantId claim and overrides
    // whatever the client sent (MerchantIdHeaderRequest) — so the merchant id to assert against
    // is the one baked into the token, not an independently-generated one.
    UUID merchantId = UUID.randomUUID();
    String authHeader = "Bearer " + jwtTokenService.issueAccessToken(merchantId.toString(), "ROLE_MERCHANT", merchantId);
    RequestEntity<Void> request =
        RequestEntity.get(
                URI.create("http://localhost:" + port + "/api/v1/payments?status=CAPTURED&q=Elena&page=1&size=5"))
            .header("Authorization", authHeader)
            .build();

    ResponseEntity<String> response = restTemplate.exchange(request, String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(lastQueryPath).isEqualTo("/payments");
    assertThat(lastQueryQuery)
        .contains("status=CAPTURED")
        .contains("q=Elena")
        .contains("page=1")
        .contains("size=5");
    assertThat(lastQueryMerchantIdHeader).isEqualTo(merchantId.toString());
    assertThat(lastQueryAuthorizationHeader).isEqualTo(authHeader);
  }

  @Test
  void platformAccountBalanceProxiesToTheLedgerService() {
    RequestEntity<Void> request =
        RequestEntity.get(URI.create("http://localhost:" + port + "/accounts/Platform%20Bank/balance"))
            .header("Authorization", merchantAuthHeader())
            .build();

    ResponseEntity<String> response = restTemplate.exchange(request, String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(lastLedgerPath).isEqualTo("/accounts/Platform Bank/balance");
  }

  @Test
  void merchantAccountBalanceProxiesToTheLedgerService() {
    UUID merchantId = UUID.randomUUID();
    RequestEntity<Void> request =
        RequestEntity.get(
                URI.create("http://localhost:" + port + "/accounts/" + merchantId + "/merchant-payable/balance"))
            .header("Authorization", merchantAuthHeader())
            .build();

    ResponseEntity<String> response = restTemplate.exchange(request, String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(lastLedgerPath).isEqualTo("/accounts/" + merchantId + "/merchant-payable/balance");
  }

  @Test
  void ledgerEntriesProxiesTheTransactionQueryParamToTheLedgerService() {
    RequestEntity<Void> request =
        RequestEntity.get(URI.create("http://localhost:" + port + "/ledger?transaction=demo-ref-001"))
            .header("Authorization", merchantAuthHeader())
            .build();

    ResponseEntity<String> response = restTemplate.exchange(request, String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(lastLedgerPath).isEqualTo("/ledger");
    assertThat(lastLedgerQuery).contains("transaction=demo-ref-001");
  }

  @Test
  void createPayoutProxiesTheBodyToTheLedgerService() {
    UUID merchantId = UUID.randomUUID();
    RequestEntity<String> request =
        RequestEntity.post(URI.create("http://localhost:" + port + "/payouts"))
            .header("Content-Type", "application/json")
            .header("Authorization", merchantAuthHeader())
            .body("{\"merchantId\":\"" + merchantId + "\"}");

    ResponseEntity<String> response = restTemplate.exchange(request, String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(lastLedgerMethod).isEqualTo("POST");
    assertThat(lastLedgerPath).isEqualTo("/payouts");
    assertThat(lastLedgerBody).isEqualTo("{\"merchantId\":\"" + merchantId + "\"}");
  }

  @Test
  void getPayoutProxiesToTheLedgerService() {
    UUID payoutId = UUID.randomUUID();
    RequestEntity<Void> request =
        RequestEntity.get(URI.create("http://localhost:" + port + "/payouts/" + payoutId))
            .header("Authorization", merchantAuthHeader())
            .build();

    ResponseEntity<String> response = restTemplate.exchange(request, String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(lastLedgerPath).isEqualTo("/payouts/" + payoutId);
  }

  @Test
  void operatorPayoutsSweepProxiesToTheLedgerServiceSweepRoute() {
    String operatorToken = "Bearer " + jwtTokenService.issueAccessToken("operator-1", "ROLE_OPERATOR", null);
    RequestEntity<Void> request =
        RequestEntity.post(URI.create("http://localhost:" + port + "/operator/payouts/sweep"))
            .header("Authorization", operatorToken)
            .build();

    ResponseEntity<String> response = restTemplate.exchange(request, String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(lastLedgerMethod).isEqualTo("POST");
    assertThat(lastLedgerPath).isEqualTo("/payouts/sweep");
  }

  @Test
  void operatorPayoutsSweepIsForbiddenForAMerchantToken() {
    RequestEntity<Void> request =
        RequestEntity.post(URI.create("http://localhost:" + port + "/operator/payouts/sweep"))
            .header("Authorization", merchantAuthHeader())
            .build();

    ResponseEntity<String> response = restTemplate.exchange(request, String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(403);
  }

  @Test
  void paymentsSummaryForwardsDaysMerchantIdAndAuthorizationToThePaymentQueryService() {
    UUID merchantId = UUID.randomUUID();
    String authHeader = "Bearer " + jwtTokenService.issueAccessToken(merchantId.toString(), "ROLE_MERCHANT", merchantId);
    RequestEntity<Void> request =
        RequestEntity.get(URI.create("http://localhost:" + port + "/api/v1/payments/summary?days=7"))
            .header("Authorization", authHeader)
            .build();

    ResponseEntity<String> response = restTemplate.exchange(request, String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).contains("netVolumeMinor");
    assertThat(lastQueryPath).isEqualTo("/payments/summary");
    assertThat(lastQueryQuery).contains("days=7");
    assertThat(lastQueryMerchantIdHeader).isEqualTo(merchantId.toString());
    assertThat(lastQueryAuthorizationHeader).isEqualTo(authHeader);
  }

  @Test
  void paymentTimelineProxiesToThePaymentQueryServiceEventsRoute() {
    RequestEntity<Void> request =
        RequestEntity.get(URI.create("http://localhost:" + port + "/api/v1/payments/abc-123/events"))
            .header("Authorization", merchantAuthHeader())
            .build();

    ResponseEntity<String> response = restTemplate.exchange(request, String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(lastQueryPath).isEqualTo("/payments/abc-123/events");
  }

  @Test
  void operatorLedgerEntriesForwardsSincePageAndSizeToTheLedgerService() {
    String operatorToken = "Bearer " + jwtTokenService.issueAccessToken("operator-1", "ROLE_OPERATOR", null);
    RequestEntity<Void> request =
        RequestEntity.get(
                URI.create(
                    "http://localhost:" + port + "/operator/ledger/entries?since=2026-01-01T00:00:00Z&page=1&size=5"))
            .header("Authorization", operatorToken)
            .build();

    ResponseEntity<String> response = restTemplate.exchange(request, String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(lastLedgerPath).isEqualTo("/operator/ledger/entries");
    assertThat(lastLedgerQuery).contains("since=2026-01-01T00:00:00Z").contains("page=1").contains("size=5");
  }

  @Test
  void operatorLedgerEntriesIsForbiddenForAMerchantToken() {
    RequestEntity<Void> request =
        RequestEntity.get(URI.create("http://localhost:" + port + "/operator/ledger/entries"))
            .header("Authorization", merchantAuthHeader())
            .build();

    ResponseEntity<String> response = restTemplate.exchange(request, String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(403);
  }

  @Test
  void webhookDeliveriesForwardsMerchantIdPageAndSizeToTheNotificationService() {
    UUID merchantId = UUID.randomUUID();
    String authHeader = "Bearer " + jwtTokenService.issueAccessToken(merchantId.toString(), "ROLE_MERCHANT", merchantId);
    RequestEntity<Void> request =
        RequestEntity.get(URI.create("http://localhost:" + port + "/webhooks/deliveries?page=1&size=5"))
            .header("Authorization", authHeader)
            .build();

    ResponseEntity<String> response = restTemplate.exchange(request, String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(lastNotificationPath).isEqualTo("/webhooks/deliveries");
    assertThat(lastNotificationQuery).contains("page=1").contains("size=5");
    assertThat(lastNotificationMerchantIdHeader).isEqualTo(merchantId.toString());
  }
}
