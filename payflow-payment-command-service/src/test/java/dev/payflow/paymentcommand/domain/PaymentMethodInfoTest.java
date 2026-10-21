package dev.payflow.paymentcommand.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentMethodInfoTest {

  @Test
  void aCardStartingWith4IsDerivedAsVisa() {
    assertThat(PaymentMethodInfo.deriveMethod("4242424242424242"))
        .isEqualTo(new PaymentMethodInfo("Visa", "4242"));
  }

  @Test
  void aCardStartingWith5IsDerivedAsMastercard() {
    assertThat(PaymentMethodInfo.deriveMethod("5500000000000004"))
        .isEqualTo(new PaymentMethodInfo("Mastercard", "0004"));
  }

  @Test
  void aCardStartingWith3IsDerivedAsAmex() {
    assertThat(PaymentMethodInfo.deriveMethod("340000000000009"))
        .isEqualTo(new PaymentMethodInfo("Amex", "0009"));
  }

  @Test
  void anyOtherCardFallsBackToACardBrand() {
    assertThat(PaymentMethodInfo.deriveMethod("6011000000000004"))
        .isEqualTo(new PaymentMethodInfo("Card", "0004"));
  }

  @Test
  void aNullCardNumberFallsBackToCardAndZeroLast4() {
    assertThat(PaymentMethodInfo.deriveMethod(null)).isEqualTo(new PaymentMethodInfo("Card", "0000"));
  }

  @Test
  void aBlankCardNumberFallsBackToZeroLast4() {
    assertThat(PaymentMethodInfo.deriveMethod("   ")).isEqualTo(new PaymentMethodInfo("Card", "0000"));
  }
}
