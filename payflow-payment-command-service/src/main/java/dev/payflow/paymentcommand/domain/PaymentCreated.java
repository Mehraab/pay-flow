package dev.payflow.paymentcommand.domain;

import dev.payflow.common.domain.Money;
import dev.payflow.cqrs.domain.EventMetadata;
import java.util.UUID;

public record PaymentCreated(
    EventMetadata metadata,
    PaymentId id,
    UUID merchantId,
    Money amount,
    String idempotencyKey,
    String customer,
    PaymentMethodInfo paymentMethod)
    implements PaymentDomainEvent {

  /** Preserves the pre-BE-03 shape so older call sites (and tests) keep compiling unchanged. */
  public PaymentCreated(
      EventMetadata metadata, PaymentId id, UUID merchantId, Money amount, String idempotencyKey) {
    this(metadata, id, merchantId, amount, idempotencyKey, "Customer", new PaymentMethodInfo("Visa", "4242"));
  }
}
