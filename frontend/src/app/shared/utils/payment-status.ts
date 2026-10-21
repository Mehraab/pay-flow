export type StatusPillVariant = 'captured' | 'pending' | 'failed' | 'refunded';

// Real status vocabulary (payflow-payment-query-service's PaymentView.status):
// CREATED, AUTHORIZED, CAPTURED, FAILED, REFUNDED. CREATED/AUTHORIZED are both
// in-flight states, so both map onto the UI's single "pending" bucket/chip.
const VARIANT_BY_STATUS: Record<string, StatusPillVariant> = {
  CREATED: 'pending',
  AUTHORIZED: 'pending',
  CAPTURED: 'captured',
  FAILED: 'failed',
  REFUNDED: 'refunded',
};

const LABEL_BY_STATUS: Record<string, string> = {
  CREATED: 'Created',
  AUTHORIZED: 'Authorized',
  CAPTURED: 'Captured',
  FAILED: 'Failed',
  REFUNDED: 'Refunded',
};

export function statusPillVariant(status: string): StatusPillVariant {
  return VARIANT_BY_STATUS[status] ?? 'pending';
}

export function statusLabel(status: string): string {
  return LABEL_BY_STATUS[status] ?? status;
}
