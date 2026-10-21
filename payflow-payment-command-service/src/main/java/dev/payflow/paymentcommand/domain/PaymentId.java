package dev.payflow.paymentcommand.domain;

import dev.payflow.cqrs.domain.ValueObject;
import java.util.UUID;

public record PaymentId(UUID value) implements ValueObject {

  public PaymentId {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
  }

  public static PaymentId generate() {
    return new PaymentId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
