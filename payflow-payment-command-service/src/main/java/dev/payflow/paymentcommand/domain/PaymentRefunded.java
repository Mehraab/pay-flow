package dev.payflow.paymentcommand.domain;

import dev.payflow.cqrs.domain.EventMetadata;

public record PaymentRefunded(EventMetadata metadata) implements PaymentDomainEvent {}
