/** STUB(BE-04) — no real endpoint exists yet. */
export interface DailyVolumePoint {
  date: string;
  amountMinor: number;
}

export interface PaymentsSummary {
  netVolumeMinor: number;
  successRatePct: number;
  avgAuthTimeMs: number;
  dailyVolume: DailyVolumePoint[];
}
