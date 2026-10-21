package dev.payflow.paymentquery.view;

import java.time.Instant;
import java.util.UUID;

public record PaymentView(
    UUID paymentId,
    UUID merchantId,
    long amountMinor,
    String currency,
    String status,
    String providerRef,
    String declineReason,
    Instant createdAt,
    String customer,
    PaymentMethodView paymentMethod,
    String idempotencyKey,
    String providerDisplayName) {

  /** Preserves the pre-BE-03 shape so older call sites (and tests) keep compiling unchanged. */
  public PaymentView(
      UUID paymentId,
      UUID merchantId,
      long amountMinor,
      String currency,
      String status,
      String providerRef,
      String declineReason) {
    this(
        paymentId,
        merchantId,
        amountMinor,
        currency,
        status,
        providerRef,
        declineReason,
        Instant.now(),
        "Customer",
        new PaymentMethodView("Visa", "4242"),
        null,
        providerRef != null ? "Mock Simulator" : null);
  }
}
