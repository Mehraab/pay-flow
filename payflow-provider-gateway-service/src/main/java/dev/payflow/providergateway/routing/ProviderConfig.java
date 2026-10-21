package dev.payflow.providergateway.routing;

/** A routable payment processor and the operator-controlled settings that govern BR-13 routing. */
public record ProviderConfig(String id, String name, int priority, boolean active) {}
