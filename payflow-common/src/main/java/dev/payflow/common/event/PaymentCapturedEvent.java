package dev.payflow.common.event;

import dev.payflow.cqrs.domain.EventMetadata;
import java.util.UUID;

public record PaymentCapturedEvent(EventMetadata metadata, UUID paymentId, String providerRef)
    implements PaymentEvent {}
