export type Currency = 'USD' | 'EUR' | 'GBP';

export type PaymentStatus = 'PENDING' | 'CAPTURED' | 'DECLINED' | 'REFUNDED';

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
 * Extends PaymentView with fields BE-03 will add server-side. Used only by
 * STUB(BE-02) list data until BE-03 ships.
 */
export interface PaymentViewEnriched extends PaymentView {
  customerName: string;
  method: string;
  createdAt: string;
  idempotencyKey: string;
}

export interface CreatePaymentRequest {
  merchantId: string;
  amountMinor: number;
  currency: Currency;
  cardNumber: string;
  forceLedgerFailure: boolean;
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
