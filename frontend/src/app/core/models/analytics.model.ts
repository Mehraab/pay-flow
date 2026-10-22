export interface DailyVolumePoint {
  date: string;
  amountMinor: number;
}

/** Real shape of payflow-payment-query-service's PaymentSummaryResponse (BE-04). */
export interface PaymentsSummary {
  netVolumeMinor: number;
  currency: string;
  volumeDeltaPct: number;
  successRatePct: number;
  /** Seconds, not ms — PaymentSummaryResponse.avgAuthSeconds. */
  avgAuthSeconds: number;
  dailyVolume: DailyVolumePoint[];
}
