package dev.payflow.paymentcommand.domain;

import dev.payflow.cqrs.domain.EventMetadata;

public record PaymentAuthorized(EventMetadata metadata, String providerRef)
    implements PaymentDomainEvent {}
