package dev.payflow.providergateway.routing;

/** A routable payment processor and the operator-controlled settings that govern BR-13 routing. */
public record ProviderConfig(
    String id, String name, int priority, boolean active, String healthStatus, double successRatePct, int avgLatencyMs) {

  /** Preserves the pre-BE-07 shape so older call sites (and tests) keep compiling unchanged. */
  public ProviderConfig(String id, String name, int priority, boolean active) {
    this(id, name, priority, active, active ? "HEALTHY" : "DEGRADED", 99.4, 145);
  }
}
