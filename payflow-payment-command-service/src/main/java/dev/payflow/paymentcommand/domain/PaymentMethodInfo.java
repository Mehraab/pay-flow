package dev.payflow.paymentcommand.domain;

/**
 * The only trace of a card's identity this system ever keeps — the raw card number itself is
 * derived from and then discarded, never stored or exposed (AGENT_AND_DEV_GUIDELINES.md, Rule 1:
 * zero framework imports, a pure value type).
 */
public record PaymentMethodInfo(String brand, String last4) {

  public static PaymentMethodInfo deriveMethod(String cardNumber) {
    return new PaymentMethodInfo(deriveBrand(cardNumber), deriveLast4(cardNumber));
  }

  private static String deriveBrand(String cardNumber) {
    if (cardNumber == null) {
      return "Card";
    }
    if (cardNumber.startsWith("4")) {
      return "Visa";
    }
    if (cardNumber.startsWith("5")) {
      return "Mastercard";
    }
    if (cardNumber.startsWith("3")) {
      return "Amex";
    }
    return "Card";
  }

  private static String deriveLast4(String cardNumber) {
    if (cardNumber == null || cardNumber.isBlank()) {
      return "0000";
    }
    return cardNumber.length() <= 4 ? cardNumber : cardNumber.substring(cardNumber.length() - 4);
  }
}
