package dev.payflow.common.dto;

/** {@code providerRef} is set only on {@link AuthorizationStatus#APPROVED}; {@code declineReason}
 * only on {@link AuthorizationStatus#DECLINED}. */
public record AuthorizePaymentResponse(
    AuthorizationStatus status, String providerRef, String declineReason) {}
