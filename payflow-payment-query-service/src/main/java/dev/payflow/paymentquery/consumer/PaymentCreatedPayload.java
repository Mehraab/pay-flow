package dev.payflow.paymentquery.consumer;

import dev.payflow.common.domain.Money;
import dev.payflow.cqrs.domain.EventMetadata;
import dev.payflow.paymentquery.view.PaymentMethodView;
import java.util.UUID;

/**
 * Only the fields this read model actually needs from {@code payment.created}'s JSON body —
 * Jackson ignores the rest ({@code id}) rather than failing on them, since Spring Boot's
 * autoconfigured {@code ObjectMapper} disables fail-on-unknown-properties. {@code customer},
 * {@code paymentMethod} and {@code metadata} are all optional since an older (pre-BE-03)
 * {@code payment.created} payload carries none of them.
 */
record PaymentCreatedPayload(
    UUID merchantId,
    Money amount,
    String idempotencyKey,
    String customer,
    PaymentMethodView paymentMethod,
    EventMetadata metadata) {}
