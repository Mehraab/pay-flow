package dev.payflow.common.dto;

/**
 * Request body for {@code POST /internal/providers/void} — not in Task 1's enumerated DTO list,
 * but required by Task 2's void endpoint; kept alongside the other provider DTOs for consistency.
 */
public record VoidChargeRequest(String providerRef) {}
