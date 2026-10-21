package dev.payflow.paymentquery.consumer;

/** Only the field this read model needs from {@code payment.failed}'s JSON body. */
record PaymentFailedPayload(String declineReason) {}
