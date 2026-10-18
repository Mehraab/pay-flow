package dev.payflow.paymentcommand.domain;

import dev.payflow.cqrs.domain.DomainEvent;

public sealed interface PaymentDomainEvent extends DomainEvent
    permits PaymentCreated, PaymentAuthorized, PaymentCaptured, PaymentFailed {}
