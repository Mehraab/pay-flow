import { PaymentEvent, PaymentViewEnriched } from '../models';

/** Wire shape of payflow-payment-query-service's PaymentView (via the gateway, unchanged). */
export interface PaymentApiResponse {
  paymentId: string;
  merchantId: string;
  amountMinor: number;
  currency: string;
  status: string;
  providerRef: string | null;
  declineReason: string | null;
  createdAt: string;
  customer: string | null;
  paymentMethod: { brand: string; last4: string } | null;
  idempotencyKey: string | null;
  providerDisplayName: string | null;
}

export interface PageResponseApi<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Wire shape of PaymentTimelineEvent (BE-05). */
export interface PaymentTimelineEventApi {
  eventType: string;
  status: string;
  timestamp: string;
  description: string;
}

export function toPaymentViewEnriched(raw: PaymentApiResponse): PaymentViewEnriched {
  return {
    paymentId: raw.paymentId,
    merchantId: raw.merchantId,
    amountMinor: raw.amountMinor,
    currency: raw.currency as PaymentViewEnriched['currency'],
    status: raw.status,
    providerRef: raw.providerRef,
    declineReason: raw.declineReason,
    customerName: raw.customer ?? undefined,
    method: raw.paymentMethod ? `${raw.paymentMethod.brand} •••• ${raw.paymentMethod.last4}` : undefined,
    createdAt: raw.createdAt,
    idempotencyKey: raw.idempotencyKey ?? undefined,
  };
}

const EVENT_LABEL_BY_TYPE: Record<string, string> = {
  PAYMENT_CREATED: 'Payment created',
  PAYMENT_AUTHORIZED: 'Authorized',
  PAYMENT_CAPTURED: 'Captured',
  LEDGER_POSTED: 'Ledger entry posted',
  WEBHOOK_DELIVERED: 'Webhook delivered',
  PAYMENT_FAILED: 'Declined',
  PAYMENT_REFUNDED: 'Refunded',
};

function humanizeEventType(eventType: string): string {
  return eventType
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

/**
 * Every real timeline event already happened (it's an append-only log), so there's no
 * "pending" step the way the old mock narrative had one — only FAILED renders red.
 * `note` (the HTML's red .tnote line) stays reserved for the failure message, same as
 * the design's original intent; non-failure steps don't carry a second line.
 */
export function toPaymentEvent(raw: PaymentTimelineEventApi): PaymentEvent {
  const isFailed = raw.status === 'FAILED';
  return {
    label: EVENT_LABEL_BY_TYPE[raw.eventType] ?? humanizeEventType(raw.eventType),
    timestamp: raw.timestamp,
    status: isFailed ? 'failed' : 'done',
    note: isFailed ? raw.description : undefined,
  };
}
