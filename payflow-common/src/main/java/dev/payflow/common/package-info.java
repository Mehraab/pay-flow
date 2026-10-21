/**
 * Shared types every PayFlow service may depend on, built on {@code common-cqrs}.
 *
 * <ul>
 *   <li>{@code domain} — value objects ({@link dev.payflow.common.domain.Money}) and enums
 *       shared across bounded contexts; subject to Rule 1 (Domain Purity), enforced by this
 *       module's own {@code DomainPurityArchitectureTest}.
 *   <li>{@code event} — the {@link dev.payflow.common.event.PaymentEvent} sealed hierarchy:
 *       outbox payload shapes shared between a publishing service and its consumers.
 *   <li>{@code dto} — inter-service REST transfer objects (e.g. the Provider Gateway's
 *       authorize/void contract); not domain types, not subject to Rule 1.
 * </ul>
 */
package dev.payflow.common;
