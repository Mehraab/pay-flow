export interface LedgerEntryResponse {
  id: string;
  transactionRef: string;
  debitAccountId: string;
  creditAccountId: string;
  amountMinor: number;
  postedAt: string;
}

export interface AccountBalanceResponse {
  accountName: string;
  balanceMinor: number;
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
