package dev.payflow.providergateway.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class ProviderRoutingServiceTest {

  @Test
  void listProvidersReturnsTheSeededProvidersOrderedByPriority() {
    ProviderRoutingService service = new ProviderRoutingService();

    List<ProviderConfig> providers = service.listProviders();

    assertThat(providers).extracting(ProviderConfig::id).containsExactly("mock-primary", "mock-secondary");
  }

  @Test
  void updateRoutingChangesPriorityAndActiveFlag() {
    ProviderRoutingService service = new ProviderRoutingService();

    ProviderConfig updated = service.updateRouting("mock-secondary", new UpdateRoutingRequest(0, false));

    assertThat(updated.priority()).isEqualTo(0);
    assertThat(updated.active()).isFalse();
    assertThat(service.listProviders()).extracting(ProviderConfig::id).first().isEqualTo("mock-secondary");
  }

  @Test
  void updateRoutingLeavesAnOmittedFieldUnchanged() {
    ProviderRoutingService service = new ProviderRoutingService();

    ProviderConfig updated = service.updateRouting("mock-primary", new UpdateRoutingRequest(null, false));

    assertThat(updated.priority()).isEqualTo(1);
    assertThat(updated.active()).isFalse();
  }

  @Test
  void updateRoutingThrowsForAnUnknownProviderId() {
    ProviderRoutingService service = new ProviderRoutingService();

    assertThatThrownBy(() -> service.updateRouting("unknown", new UpdateRoutingRequest(1, true)))
        .isInstanceOf(NoSuchElementException.class);
  }
}
