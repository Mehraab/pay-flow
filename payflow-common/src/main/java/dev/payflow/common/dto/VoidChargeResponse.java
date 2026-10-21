package dev.payflow.common.dto;

/** Response body for {@code POST /internal/providers/void}. */
public record VoidChargeResponse(String status, String providerRef) {}
