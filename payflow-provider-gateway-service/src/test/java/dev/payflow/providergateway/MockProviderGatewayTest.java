package dev.payflow.providergateway;

import static org.assertj.core.api.Assertions.assertThat;

import dev.payflow.common.domain.Currency;
import dev.payflow.common.domain.Money;
import dev.payflow.common.dto.AuthorizationStatus;
import dev.payflow.common.dto.AuthorizePaymentRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MockProviderGatewayTest {

  private final MockProviderGateway gateway = new MockProviderGateway();

  @Test
  void aCardEndingIn4242IsApproved() {
    var request =
        new AuthorizePaymentRequest(
            UUID.randomUUID(), Money.of(21400, Currency.USD), "4242424242424242");

    var response = gateway.charge(request);

    assertThat(response.status()).isEqualTo(AuthorizationStatus.APPROVED);
    assertThat(response.providerRef()).startsWith("mock-ch-");
    assertThat(response.declineReason()).isNull();
  }

  @Test
  void anyOtherCardIsDeclinedInMilestoneOne() {
    var request =
        new AuthorizePaymentRequest(
            UUID.randomUUID(), Money.of(21400, Currency.USD), "4000000000000002");

    var response = gateway.charge(request);

    assertThat(response.status()).isEqualTo(AuthorizationStatus.DECLINED);
    assertThat(response.providerRef()).isNull();
    assertThat(response.declineReason()).isEqualTo("Card not supported in Milestone 1");
  }

  @Test
  void voidChargeCompletesWithoutError() {
    gateway.voidCharge("mock-ch-abc123"); // Milestone 1 stub: no exception is the whole contract
  }
}
