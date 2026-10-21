package dev.payflow.common.event;

import dev.payflow.cqrs.domain.EventMetadata;
import java.util.UUID;

public record PaymentRefundedEvent(EventMetadata metadata, UUID paymentId) implements PaymentEvent {}
