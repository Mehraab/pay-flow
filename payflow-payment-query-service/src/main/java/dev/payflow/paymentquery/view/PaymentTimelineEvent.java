package dev.payflow.paymentquery.view;

import java.time.Instant;

/** One row of a payment's event timeline (BE-05) — the console's per-payment activity feed. */
public record PaymentTimelineEvent(String eventType, String status, Instant timestamp, String description) {}
