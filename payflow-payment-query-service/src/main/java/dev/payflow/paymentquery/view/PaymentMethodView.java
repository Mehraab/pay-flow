package dev.payflow.paymentquery.view;

/**
 * Read-side mirror of {@code payflow-payment-command-service}'s {@code PaymentMethodInfo} — this
 * module has no dependency on the command service, so the shape is duplicated rather than
 * shared; the two JSON structures ({@code brand}, {@code last4}) line up exactly.
 */
public record PaymentMethodView(String brand, String last4) {}
