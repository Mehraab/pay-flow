package dev.payflow.providergateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.payflow.common.domain.Currency;
import dev.payflow.common.domain.Money;
import dev.payflow.common.dto.AuthorizationStatus;
import dev.payflow.common.dto.AuthorizePaymentRequest;
import dev.payflow.common.dto.VoidChargeResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MockProviderGatewayTest {

  private final MockProviderGateway gateway = new MockProviderGateway();

  private AuthorizePaymentRequest requestFor(String cardNumber) {
    return new AuthorizePaymentRequest(UUID.randomUUID(), Money.of(21400, Currency.USD), cardNumber);
  }

  @Test
  void aCardEndingIn4242IsApproved() {
    var response = gateway.charge(requestFor("4242424242424242"));

    assertThat(response.status()).isEqualTo(AuthorizationStatus.APPROVED);
    assertThat(response.providerRef()).startsWith("mock-ch-");
    assertThat(response.declineReason()).isNull();
  }

  @Test
  void aCardEndingIn0002IsDeclinedForInsufficientFunds() {
    var response = gateway.charge(requestFor("4000000000000002"));

    assertThat(response.status()).isEqualTo(AuthorizationStatus.DECLINED);
    assertThat(response.providerRef()).isNull();
    assertThat(response.declineReason()).isEqualTo("Insufficient funds");
  }

  @Test
  void aCardEndingIn0069IsDeclinedForAnExpiredCard() {
    var response = gateway.charge(requestFor("4000000000000069"));

    assertThat(response.status()).isEqualTo(AuthorizationStatus.DECLINED);
    assertThat(response.providerRef()).isNull();
    assertThat(response.declineReason()).isEqualTo("Expired card");
  }

  @Test
  void aCardEndingIn0127IsDeclinedGenerically() {
    var response = gateway.charge(requestFor("4000000000000127"));

    assertThat(response.status()).isEqualTo(AuthorizationStatus.DECLINED);
    assertThat(response.providerRef()).isNull();
    assertThat(response.declineReason()).isEqualTo("Generic decline");
  }

  @Test
  void anUnrecognizedCardIsDeclinedGenericallyToo() {
    var response = gateway.charge(requestFor("4000000000009999"));

    assertThat(response.status()).isEqualTo(AuthorizationStatus.DECLINED);
    assertThat(response.declineReason()).isEqualTo("Generic decline");
  }

  @Test
  void aCardEndingIn0119SimulatesAProcessorTimeout() {
    Instant start = Instant.now();

    assertThatThrownBy(() -> gateway.charge(requestFor("4000000000000119")))
        .isInstanceOf(ProviderTimeoutException.class);

    assertThat(Duration.between(start, Instant.now())).isGreaterThanOrEqualTo(Duration.ofMillis(4900));
  }

  @Test
  void voidChargeReturnsAVoidedResponseEchoingTheProviderRef() {
    VoidChargeResponse response = gateway.voidCharge("mock-ch-abc123");

    assertThat(response.status()).isEqualTo("VOIDED");
    assertThat(response.providerRef()).isEqualTo("mock-ch-abc123");
  }
}
