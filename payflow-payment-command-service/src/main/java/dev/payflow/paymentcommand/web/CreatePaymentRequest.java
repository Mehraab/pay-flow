package dev.payflow.paymentcommand.web;

import java.util.UUID;

public record CreatePaymentRequest(
    UUID merchantId,
    long amountMinor,
    String currency,
    String cardNumber,
    Boolean forceLedgerFailure,
    String customer) {}
