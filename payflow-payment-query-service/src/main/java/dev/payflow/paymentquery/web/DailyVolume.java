package dev.payflow.paymentquery.web;

/** One bucket of a {@link PaymentSummaryResponse}'s daily chart; {@code date} is {@code YYYY-MM-DD} (UTC). */
public record DailyVolume(String date, long amountMinor) {}
