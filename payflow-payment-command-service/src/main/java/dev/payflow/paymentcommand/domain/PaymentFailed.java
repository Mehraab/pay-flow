package dev.payflow.paymentcommand.domain;

import dev.payflow.cqrs.domain.EventMetadata;

public record PaymentFailed(EventMetadata metadata, String declineReason)
    implements PaymentDomainEvent {}
