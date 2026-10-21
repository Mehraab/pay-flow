import { toPaymentEvent, toPaymentViewEnriched } from './payment-api.mapper';

describe('toPaymentViewEnriched', () => {
  it('maps customer and paymentMethod onto customerName/method', () => {
    const result = toPaymentViewEnriched({
      paymentId: 'p1',
      merchantId: 'm1',
      amountMinor: 1000,
      currency: 'USD',
      status: 'CAPTURED',
      providerRef: 'pr_1',
      declineReason: null,
      createdAt: '2026-01-01T00:00:00Z',
      customer: 'Ava Thompson',
      paymentMethod: { brand: 'Visa', last4: '4242' },
      idempotencyKey: 'idem_1',
      providerDisplayName: 'Mock Simulator',
    });

    expect(result.customerName).toBe('Ava Thompson');
    expect(result.method).toBe('Visa •••• 4242');
    expect(result.createdAt).toBe('2026-01-01T00:00:00Z');
    expect(result.idempotencyKey).toBe('idem_1');
  });

  it('leaves the enriched fields undefined when the wire value is null', () => {
    const result = toPaymentViewEnriched({
      paymentId: 'p1',
      merchantId: 'm1',
      amountMinor: 1000,
      currency: 'USD',
      status: 'CREATED',
      providerRef: null,
      declineReason: null,
      createdAt: '2026-01-01T00:00:00Z',
      customer: null,
      paymentMethod: null,
      idempotencyKey: null,
      providerDisplayName: null,
    });

    expect(result.customerName).toBeUndefined();
    expect(result.method).toBeUndefined();
    expect(result.idempotencyKey).toBeUndefined();
  });
});

describe('toPaymentEvent', () => {
  it('maps a non-failure event to status "done" with no note', () => {
    const result = toPaymentEvent({
      eventType: 'PAYMENT_CAPTURED',
      status: 'CAPTURED',
      timestamp: '2026-01-01T00:00:02Z',
      description: 'Payment captured successfully (Ref: pr_1)',
    });

    expect(result.label).toBe('Captured');
    expect(result.status).toBe('done');
    expect(result.note).toBeUndefined();
  });

  it('maps a FAILED event to status "failed" and carries the description as the note', () => {
    const result = toPaymentEvent({
      eventType: 'PAYMENT_FAILED',
      status: 'FAILED',
      timestamp: '2026-01-01T00:00:02Z',
      description: 'Payment declined: Insufficient funds',
    });

    expect(result.label).toBe('Declined');
    expect(result.status).toBe('failed');
    expect(result.note).toBe('Payment declined: Insufficient funds');
  });

  it('falls back to a humanized label for an unrecognized eventType', () => {
    const result = toPaymentEvent({
      eventType: 'SOME_NEW_EVENT',
      status: 'DONE',
      timestamp: '2026-01-01T00:00:00Z',
      description: 'n/a',
    });

    expect(result.label).toBe('Some New Event');
  });
});
