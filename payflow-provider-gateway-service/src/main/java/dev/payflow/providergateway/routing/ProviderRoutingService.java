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
    providers.put("mock-primary", new ProviderConfig("mock-primary", "Mock Primary Processor", 1, true));
    providers.put("mock-secondary", new ProviderConfig("mock-secondary", "Mock Backup Processor", 2, true));
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
    ProviderConfig updated = new ProviderConfig(current.id(), current.name(), priority, active);
    providers.put(id, updated);
    return updated;
  }
}
