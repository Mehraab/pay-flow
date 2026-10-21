package dev.payflow.common.event;

import dev.payflow.cqrs.domain.DomainEvent;

/**
 * Sealed so every consumer's {@code switch} over a payment event is exhaustiveness-checked by the
 * compiler — adding a new event type without updating every switch is a compile error, not a
 * runtime surprise (AGENT_AND_DEV_GUIDELINES.md, Rule 5). The permits list grows as later
 * milestones add {@code payment.authorized}.
 */
public sealed interface PaymentEvent extends DomainEvent
    permits PaymentCreatedEvent, PaymentCapturedEvent, PaymentFailedEvent, PaymentRefundedEvent {}
