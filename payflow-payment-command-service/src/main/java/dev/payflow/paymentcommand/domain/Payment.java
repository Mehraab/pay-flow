package dev.payflow.paymentcommand.domain;

import dev.payflow.common.domain.Money;
import dev.payflow.common.domain.PaymentStatus;
import dev.payflow.cqrs.domain.AggregateRoot;
import dev.payflow.cqrs.domain.EventMetadata;
import java.util.UUID;

/**
 * The write-side aggregate for a single charge. Status changes ONLY through {@code
 * authorize}/{@code capture}/{@code fail}, each of which raises an event and lets {@link
 * #apply} mutate state — there are no public setters (AGENT_AND_DEV_GUIDELINES.md, Rule 1 and the
 * common-cqrs {@code AggregateRoot} contract).
 */
public final class Payment extends AggregateRoot<PaymentId, PaymentDomainEvent> {

  private UUID merchantId;
  private Money amount;
  private PaymentStatus status;
  private String idempotencyKey;
  private String providerRef;
  private String declineReason;

  private Payment(PaymentId id, long version) {
    super(id, version);
  }

  public static Payment create(PaymentId id, UUID merchantId, Money amount, String idempotencyKey) {
    return create(id, merchantId, amount, idempotencyKey, "Customer", new PaymentMethodInfo("Visa", "4242"));
  }

  public static Payment create(
      PaymentId id,
      UUID merchantId,
      Money amount,
      String idempotencyKey,
      String customer,
      PaymentMethodInfo paymentMethod) {
    Payment payment = new Payment(id, 0);
    payment.raise(
        new PaymentCreated(
            EventMetadata.next(id, UUID.randomUUID(), null),
            id,
            merchantId,
            amount,
            idempotencyKey,
            customer,
            paymentMethod));
    return payment;
  }

  /**
   * Rebuilds a {@code Payment} directly from already-persisted state — current-state storage,
   * not event sourcing, so this sets fields directly rather than replaying events (which would
   * incorrectly re-raise them as new uncommitted events). Repository adapters only; never called
   * from application code.
   */
  public static Payment reconstitute(
      PaymentId id,
      long version,
      UUID merchantId,
      Money amount,
      PaymentStatus status,
      String idempotencyKey,
      String providerRef,
      String declineReason) {
    Payment payment = new Payment(id, version);
    payment.merchantId = merchantId;
    payment.amount = amount;
    payment.status = status;
    payment.idempotencyKey = idempotencyKey;
    payment.providerRef = providerRef;
    payment.declineReason = declineReason;
    return payment;
  }

  public void authorize(String providerRef) {
    if (status != PaymentStatus.CREATED) {
      throw new IllegalStateException(
          "Cannot authorize payment %s in status %s".formatted(id(), status));
    }
    raise(new PaymentAuthorized(EventMetadata.next(id(), UUID.randomUUID(), null), providerRef));
  }

  public void capture(String providerRef) {
    if (status != PaymentStatus.CREATED && status != PaymentStatus.AUTHORIZED) {
      throw new IllegalStateException(
          "Cannot capture payment %s in status %s".formatted(id(), status));
    }
    raise(new PaymentCaptured(EventMetadata.next(id(), UUID.randomUUID(), null), providerRef));
  }

  public void fail(String declineReason) {
    if (status == PaymentStatus.CAPTURED) {
      throw new IllegalStateException(
          "Cannot fail payment %s that is already captured".formatted(id()));
    }
    raise(new PaymentFailed(EventMetadata.next(id(), UUID.randomUUID(), null), declineReason));
  }

  public void refund() {
    if (status != PaymentStatus.CAPTURED) {
      throw new IllegalStateException(
          "Cannot refund payment %s in status %s".formatted(id(), status));
    }
    raise(new PaymentRefunded(EventMetadata.next(id(), UUID.randomUUID(), null)));
  }

  @Override
  protected void apply(PaymentDomainEvent event) {
    switch (event) {
      case PaymentCreated e -> {
        this.merchantId = e.merchantId();
        this.amount = e.amount();
        this.idempotencyKey = e.idempotencyKey();
        this.status = PaymentStatus.CREATED;
      }
      case PaymentAuthorized e -> {
        this.providerRef = e.providerRef();
        this.status = PaymentStatus.AUTHORIZED;
      }
      case PaymentCaptured e -> {
        this.providerRef = e.providerRef();
        this.status = PaymentStatus.CAPTURED;
      }
      case PaymentFailed e -> {
        this.declineReason = e.declineReason();
        this.status = PaymentStatus.FAILED;
      }
      case PaymentRefunded e -> this.status = PaymentStatus.REFUNDED;
    }
  }

  public UUID merchantId() {
    return merchantId;
  }

  public Money amount() {
    return amount;
  }

  public PaymentStatus status() {
    return status;
  }

  public String idempotencyKey() {
    return idempotencyKey;
  }

  public String providerRef() {
    return providerRef;
  }

  public String declineReason() {
    return declineReason;
  }
}
