package dev.payflow.paymentquery.consumer;

import dev.payflow.common.domain.Money;
import java.util.UUID;

/**
 * Only the fields this read model actually needs from {@code payment.created}'s JSON body —
 * Jackson ignores the rest ({@code metadata}, {@code id}, {@code idempotencyKey}) rather than
 * failing on them, since Spring Boot's autoconfigured {@code ObjectMapper} disables
 * fail-on-unknown-properties.
 */
record PaymentCreatedPayload(UUID merchantId, Money amount) {}
