package dev.payflow.paymentquery.consumer;

/** Only the field this read model needs from {@code payment.captured}'s JSON body. */
record PaymentCapturedPayload(String providerRef) {}
