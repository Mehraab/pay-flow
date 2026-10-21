package dev.payflow.providergateway.routing;

/** Partial update for a provider's routing settings — either field may be omitted. */
public record UpdateRoutingRequest(Integer priority, Boolean active) {}
