export type Currency = 'USD' | 'EUR' | 'GBP';

/** Real vocabulary from payflow-payment-query-service's PaymentView — not PENDING/DECLINED. */
export type PaymentStatus = 'CREATED' | 'AUTHORIZED' | 'CAPTURED' | 'FAILED' | 'REFUNDED';

export interface PaymentView {
  paymentId: string;
  merchantId: string;
  amountMinor: number;
  currency: Currency;
  status: PaymentStatus | string;
  providerRef: string | null;
  declineReason: string | null;
}

/**
 * Extends PaymentView with the BE-03 fields (now shipped — see payment-api.mapper.ts,
 * which maps the real customer/paymentMethod wire fields onto these). Kept optional
 * since a record predating BE-03 could in principle still lack them.
 */
export interface PaymentViewEnriched extends PaymentView {
  customerName?: string;
  method?: string;
  createdAt?: string;
  idempotencyKey?: string;
}

export interface CreatePaymentRequest {
  merchantId: string;
  amountMinor: number;
  currency: Currency;
  cardNumber: string;
  forceLedgerFailure: boolean;
  /** Optional — payflow-payment-command-service falls back to "Customer" when omitted. */
  customer?: string;
}

export interface CreatePaymentResponse {
  paymentId: string;
}

export interface PaymentEvent {
  label: string;
  timestamp: string;
  status: 'done' | 'pending' | 'failed';
  note?: string;
}
