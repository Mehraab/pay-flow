package dev.payflow.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void ofCreatesAMoneyValue() {
    var money = Money.of(1000, Currency.USD);

    assertThat(money.amountMinor()).isEqualTo(1000);
    assertThat(money.currency()).isEqualTo(Currency.USD);
  }

  @Test
  void negativeAmountIsRejected() {
    assertThatThrownBy(() -> new Money(-1, Currency.USD))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullCurrencyIsRejected() {
    assertThatThrownBy(() -> new Money(100, null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void zeroIsAllowed() {
    assertThat(Money.of(0, Currency.USD).amountMinor()).isZero();
  }

  @Test
  void addsTwoAmountsOfTheSameCurrency() {
    var a = Money.of(600, Currency.USD);
    var b = Money.of(150, Currency.USD);

    assertThat(a.add(b)).isEqualTo(Money.of(750, Currency.USD));
  }

  @Test
  void subtractsTwoAmountsOfTheSameCurrency() {
    var a = Money.of(600, Currency.USD);
    var b = Money.of(150, Currency.USD);

    assertThat(a.subtract(b)).isEqualTo(Money.of(450, Currency.USD));
  }

  @Test
  void addingDifferentCurrenciesIsRejected() {
    var usd = Money.of(100, Currency.USD);
    var eur = Money.of(100, Currency.EUR);

    assertThatThrownBy(() -> usd.add(eur)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void subtractingDifferentCurrenciesIsRejected() {
    var usd = Money.of(100, Currency.USD);
    var eur = Money.of(100, Currency.EUR);

    assertThatThrownBy(() -> usd.subtract(eur)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void subtractingToANegativeResultIsRejected() {
    var small = Money.of(100, Currency.USD);
    var big = Money.of(500, Currency.USD);

    assertThatThrownBy(() -> small.subtract(big)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void twoEqualMoneyValuesAreEqual() {
    assertThat(Money.of(500, Currency.USD)).isEqualTo(Money.of(500, Currency.USD));
  }
}
