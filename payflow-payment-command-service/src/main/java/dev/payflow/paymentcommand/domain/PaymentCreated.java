package dev.payflow.paymentcommand.domain;

import dev.payflow.common.domain.Money;
import dev.payflow.cqrs.domain.EventMetadata;
import java.util.UUID;

public record PaymentCreated(
    EventMetadata metadata, PaymentId id, UUID merchantId, Money amount, String idempotencyKey)
    implements PaymentDomainEvent {}
