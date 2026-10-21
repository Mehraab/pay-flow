package dev.payflow.common.event;

import dev.payflow.cqrs.domain.EventMetadata;
import java.util.UUID;

public record PaymentFailedEvent(EventMetadata metadata, UUID paymentId, String declineReason)
    implements PaymentEvent {}
