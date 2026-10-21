package dev.payflow.paymentquery.web;

import java.util.List;

public record PaymentSummaryResponse(
    long netVolumeMinor,
    String currency,
    double volumeDeltaPct,
    double successRatePct,
    double avgAuthSeconds,
    List<DailyVolume> dailyVolume) {}
