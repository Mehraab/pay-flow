package dev.payflow.providergateway.routing;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** In-memory, thread-safe store of provider routing configuration (BR-13). */
@Service
public class ProviderRoutingService {

  private final Map<String, ProviderConfig> providers = new ConcurrentHashMap<>();

  public ProviderRoutingService() {
    // BE-07: realistic per-tier health metrics — mock-primary plays the role of a major
    // processor (Stripe-tier), mock-secondary the backup (Adyen-tier).
    providers.put(
        "mock-primary", new ProviderConfig("mock-primary", "Mock Primary Processor", 1, true, "HEALTHY", 99.7, 120));
    providers.put(
        "mock-secondary",
        new ProviderConfig("mock-secondary", "Mock Backup Processor", 2, true, "HEALTHY", 99.2, 155));
  }

  public List<ProviderConfig> listProviders() {
    return providers.values().stream().sorted(Comparator.comparingInt(ProviderConfig::priority)).toList();
  }

  public ProviderConfig updateRouting(String id, UpdateRoutingRequest request) {
    ProviderConfig current = providers.get(id);
    if (current == null) {
      throw new NoSuchElementException("No provider configured with id '" + id + "'");
    }
    int priority = request.priority() != null ? request.priority() : current.priority();
    boolean active = request.active() != null ? request.active() : current.active();
    // Preserves the health metrics — only the 4-arg compatibility constructor resets them,
    // and a routing update must never silently wipe out a provider's real health data.
    ProviderConfig updated =
        new ProviderConfig(
            current.id(), current.name(), priority, active, current.healthStatus(), current.successRatePct(), current.avgLatencyMs());
    providers.put(id, updated);
    return updated;
  }
}
