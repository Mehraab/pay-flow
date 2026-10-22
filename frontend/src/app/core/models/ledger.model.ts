export interface LedgerEntryResponse {
  id: string;
  transactionRef: string;
  debitAccountId: string;
  creditAccountId: string;
  amountMinor: number;
  postedAt: string;
  /** BE-06 — always "POSTED" today, but the field exists on the real response. */
  status: string;
}

export interface AccountBalanceResponse {
  accountName: string;
  balanceMinor: number;
}

/**
 * Display view-model for one of Ledger Integrity's five platform account cards. Three
 * (Provider Settlement, Platform Bank, Platform Fee Revenue) are real platform accounts
 * fetched via GET /accounts/{accountName}/balance. The other two are per-merchant
 * accounts only (LedgerAccountService.MERCHANT_PAYABLE/PAYOUT_IN_TRANSIT) — there is no
 * all-merchants aggregate endpoint for either, so they stay genuinely mocked, not just
 * "not wired up yet".
 */
export interface PlatformAccountCard {
  label: string;
  kind: 'Asset' | 'Liability' | 'Revenue';
  balanceMinor: number;
  isMocked: boolean;
}

export interface LedgerIntegrityResponse {
  totalDebitsMinor: number;
  totalCreditsMinor: number;
  balanced: boolean;
}

export interface PayoutResponse {
  id: string;
  merchantId: string;
  amountMinor: number;
  currency: string;
  status: string;
  createdAt: string;
}
