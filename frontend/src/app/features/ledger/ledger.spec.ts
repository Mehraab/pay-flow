import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { environment } from '../../../environments/environment';
import { Ledger } from './ledger';

function payoutPayload(status: string) {
  return {
    id: 'payout-1',
    merchantId: 'merchant-1',
    amountMinor: 4579952,
    currency: 'USD',
    status,
    createdAt: '2026-01-01T00:00:00Z',
  };
}

describe('Ledger', () => {
  let fixture: ComponentFixture<Ledger>;
  let component: Ledger;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    sessionStorage.setItem(
      'payflow.auth',
      JSON.stringify({
        accessToken: 'fake-token',
        refreshToken: 'fake-refresh',
        role: 'MERCHANT',
        merchantId: 'merchant-1',
        expiresAt: Date.now() + 900_000,
      }),
    );

    TestBed.configureTestingModule({
      imports: [Ledger],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(Ledger);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);

    // Flush the two balance calls + the real entries call the constructor fires.
    httpMock.expectOne((r) => r.url.includes('/accounts/merchant-1/Merchant%20Payable/balance')).flush({
      accountName: 'Merchant Payable',
      balanceMinor: 100000,
    });
    httpMock.expectOne((r) => r.url.includes('/accounts/merchant-1/Payout%20In-Transit/balance')).flush({
      accountName: 'Payout In-Transit',
      balanceMinor: 0,
    });
    httpMock.expectOne((r) => r.url === `${environment.apiBase}/ledger/merchants/merchant-1/entries`).flush({
      content: [],
      page: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
    });
  });

  afterEach(() => {
    httpMock.verify();
    sessionStorage.removeItem('payflow.auth');
    vi.useRealTimers();
  });

  it('requests a payout, narrates In-Transit while polling, then settles to Success', async () => {
    vi.useFakeTimers();
    (component as any).requestPayout();

    const createReq = httpMock.expectOne((r) => r.method === 'POST' && r.url.endsWith('/payouts'));
    expect(createReq.request.body).toEqual({ merchantId: 'merchant-1' });
    createReq.flush(payoutPayload('IN_TRANSIT'));

    expect((component as any).payoutLoading()).toBe(true);
    expect((component as any).payout()?.status).toBe('IN_TRANSIT');

    await vi.advanceTimersByTimeAsync(2000);
    const pollReq = httpMock.expectOne((r) => r.method === 'GET' && r.url.endsWith('/payouts/payout-1'));
    pollReq.flush(payoutPayload('SETTLED'));

    expect((component as any).payoutLoading()).toBe(false);
    expect((component as any).payoutError()).toBeNull();
    expect((component as any).payout()?.status).toBe('SETTLED');

    // Settling re-fetches balances.
    httpMock.expectOne((r) => r.url.includes('/accounts/merchant-1/Merchant%20Payable/balance')).flush({
      accountName: 'Merchant Payable',
      balanceMinor: 0,
    });
    httpMock.expectOne((r) => r.url.includes('/accounts/merchant-1/Payout%20In-Transit/balance')).flush({
      accountName: 'Payout In-Transit',
      balanceMinor: 100000,
    });
  });

  it('shows a timeout error when the payout never reaches a terminal status', async () => {
    vi.useFakeTimers();
    (component as any).requestPayout();

    const createReq = httpMock.expectOne((r) => r.method === 'POST' && r.url.endsWith('/payouts'));
    createReq.flush(payoutPayload('IN_TRANSIT'));

    for (let attempt = 1; attempt <= 5; attempt++) {
      await vi.advanceTimersByTimeAsync(2000);
      const pollReq = httpMock.expectOne((r) => r.method === 'GET' && r.url.endsWith('/payouts/payout-1'));
      pollReq.flush(payoutPayload('IN_TRANSIT'));
    }

    expect((component as any).payoutLoading()).toBe(false);
    expect((component as any).payoutError()).toContain('Still in-transit');
  });

  it('shows a distinct error when the initial payout request fails outright', () => {
    (component as any).requestPayout();

    const createReq = httpMock.expectOne((r) => r.method === 'POST' && r.url.endsWith('/payouts'));
    createReq.flush('boom', { status: 500, statusText: 'Internal Server Error' });

    expect((component as any).payoutLoading()).toBe(false);
    expect((component as any).payoutError()).toContain("Couldn't request a payout");
  });
});
