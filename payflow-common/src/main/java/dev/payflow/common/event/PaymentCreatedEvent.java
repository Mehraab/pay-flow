package dev.payflow.common.event;

import dev.payflow.common.domain.Money;
import dev.payflow.cqrs.domain.EventMetadata;
import java.util.UUID;

public record PaymentCreatedEvent(
    EventMetadata metadata, UUID paymentId, UUID merchantId, Money amount, String idempotencyKey)
    implements PaymentEvent {}
