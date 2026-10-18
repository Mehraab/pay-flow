package dev.payflow.common.domain;

import dev.payflow.cqrs.domain.ValueObject;

/** An amount in minor units (cents) of a single currency. Immutable; never negative. */
public record Money(long amountMinor, Currency currency) implements ValueObject {

  public Money {
    if (amountMinor < 0) {
      throw new IllegalArgumentException("amountMinor must be >= 0, was " + amountMinor);
    }
    if (currency == null) {
      throw new IllegalArgumentException("currency must not be null");
    }
  }

  public static Money of(long amountMinor, Currency currency) {
    return new Money(amountMinor, currency);
  }

  public Money add(Money other) {
    requireSameCurrency(other);
    return new Money(this.amountMinor + other.amountMinor, this.currency);
  }

  public Money subtract(Money other) {
    requireSameCurrency(other);
    return new Money(this.amountMinor - other.amountMinor, this.currency);
  }

  private void requireSameCurrency(Money other) {
    if (this.currency != other.currency) {
      throw new IllegalArgumentException(
          "Cannot combine Money in different currencies: %s and %s"
              .formatted(this.currency, other.currency));
    }
  }
}
