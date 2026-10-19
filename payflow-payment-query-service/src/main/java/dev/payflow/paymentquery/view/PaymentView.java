package dev.payflow.paymentquery.view;

import java.util.UUID;

public record PaymentView(
    UUID paymentId, UUID merchantId, long amountMinor, String currency, String status, String providerRef) {}
