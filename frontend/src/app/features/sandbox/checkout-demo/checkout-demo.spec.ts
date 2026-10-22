import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CheckoutDemo } from './checkout-demo';

function paymentPayload(status: string) {
  return {
    paymentId: 'p1',
    merchantId: 'merchant-1',
    amountMinor: 21400,
    currency: 'USD',
    status,
    providerRef: status === 'CAPTURED' ? 'pr_1' : null,
    declineReason: status === 'FAILED' ? 'Insufficient funds' : null,
    createdAt: '2026-01-01T00:00:00Z',
    customer: 'Sandbox Customer',
    paymentMethod: { brand: 'Visa', last4: '4242' },
    idempotencyKey: 'idem_1',
    providerDisplayName: 'Mock Simulator',
  };
}

describe('CheckoutDemo', () => {
  let fixture: ComponentFixture<CheckoutDemo>;
  let component: CheckoutDemo;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CheckoutDemo],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(CheckoutDemo);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    (component as any).merchantId.set('merchant-1');
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  it('starts idle: not busy, no result shown, submit enabled once merchantId is set', () => {
    expect((component as any).isBusy()).toBe(false);
    expect((component as any).hasError()).toBe(false);
    expect((component as any).isSuccess()).toBe(false);
    expect((component as any).canSubmit()).toBe(true);
  });

  it('submits, narrates an in-flight poll, then settles to Success on a terminal status', async () => {
    vi.useFakeTimers();
    (component as any).submit();

    const createReq = httpMock.expectOne((r) => r.method === 'POST' && r.url.endsWith('/api/v1/payments'));
    expect(createReq.request.headers.get('Idempotency-Key')).toBeTruthy();
    createReq.flush({ paymentId: 'p1' }, { status: 201, statusText: 'Created' });

    expect((component as any).isSubmitting()).toBe(false);
    expect((component as any).isPolling()).toBe(true);

    await vi.advanceTimersByTimeAsync(1500);
    const poll1 = httpMock.expectOne((r) => r.method === 'GET' && r.url.endsWith('/api/v1/payments/p1'));
    poll1.flush(paymentPayload('AUTHORIZED'));

    // Still in-flight: narrates the current non-terminal status without settling.
    expect((component as any).isPolling()).toBe(true);
    expect((component as any).isSuccess()).toBe(false);
    expect((component as any).inFlightPayment()?.status).toBe('AUTHORIZED');

    await vi.advanceTimersByTimeAsync(1500);
    const poll2 = httpMock.expectOne((r) => r.method === 'GET' && r.url.endsWith('/api/v1/payments/p1'));
    poll2.flush(paymentPayload('CAPTURED'));

    expect((component as any).isPolling()).toBe(false);
    expect((component as any).isSuccess()).toBe(true);
    expect((component as any).inFlightPayment()?.status).toBe('CAPTURED');
    expect((component as any).inFlightPayment()?.providerRef).toBe('pr_1');
  });

  it('settles to REFUNDED when forceLedgerFailure drives saga compensation, sent via the header', async () => {
    vi.useFakeTimers();
    (component as any).forceLedgerFailure.set(true);
    (component as any).submit();

    const createReq = httpMock.expectOne((r) => r.method === 'POST' && r.url.endsWith('/api/v1/payments'));
    expect(createReq.request.headers.get('X-Force-Ledger-Failure')).toBe('true');
    expect(createReq.request.body.forceLedgerFailure).toBe(false);
    createReq.flush({ paymentId: 'p1' }, { status: 201, statusText: 'Created' });

    await vi.advanceTimersByTimeAsync(1500);
    const poll = httpMock.expectOne((r) => r.method === 'GET' && r.url.endsWith('/api/v1/payments/p1'));
    poll.flush(paymentPayload('REFUNDED'));

    expect((component as any).isSuccess()).toBe(true);
    expect((component as any).inFlightPayment()?.status).toBe('REFUNDED');
  });

  it('shows a distinct error when createPayment itself fails outright', () => {
    (component as any).submit();

    const createReq = httpMock.expectOne((r) => r.method === 'POST' && r.url.endsWith('/api/v1/payments'));
    createReq.flush('boom', { status: 500, statusText: 'Internal Server Error' });

    expect((component as any).isBusy()).toBe(false);
    expect((component as any).hasError()).toBe(true);
    expect((component as any).errorMessage()).toContain("Couldn't submit");
  });

  it('shows a distinct timeout error when polling never reaches a terminal status', async () => {
    vi.useFakeTimers();
    (component as any).submit();

    const createReq = httpMock.expectOne((r) => r.method === 'POST' && r.url.endsWith('/api/v1/payments'));
    createReq.flush({ paymentId: 'p1' }, { status: 201, statusText: 'Created' });

    for (let attempt = 1; attempt <= 10; attempt++) {
      await vi.advanceTimersByTimeAsync(1500);
      const poll = httpMock.expectOne((r) => r.method === 'GET' && r.url.endsWith('/api/v1/payments/p1'));
      poll.flush(paymentPayload('AUTHORIZED'));
    }

    expect((component as any).isBusy()).toBe(false);
    expect((component as any).hasError()).toBe(true);
    expect((component as any).errorMessage()).toContain('Still processing');
  });
});
