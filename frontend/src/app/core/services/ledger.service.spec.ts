import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { LedgerEntryResponse, MerchantBalances, PayoutResponse, PlatformAccountCard } from '../models';
import { LedgerService } from './ledger.service';

describe('LedgerService', () => {
  let service: LedgerService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(LedgerService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getLedgerIntegrity calls the real endpoint', () => {
    let result: { balanced: boolean } | undefined;
    service.getLedgerIntegrity().subscribe((r) => (result = r));

    const req = httpMock.expectOne(`${environment.apiBase}/operator/ledger/integrity`);
    expect(req.request.method).toBe('GET');
    req.flush({ totalDebitsMinor: 500, totalCreditsMinor: 500, balanced: true });

    expect(result?.balanced).toBe(true);
  });

  it('getPlatformAccountCards fetches the 3 real accounts and appends the 2 mocked ones', () => {
    let result: PlatformAccountCard[] | undefined;
    service.getPlatformAccountCards().subscribe((r) => (result = r));

    const providerReq = httpMock.expectOne(`${environment.apiBase}/accounts/Provider%20Settlement/balance`);
    providerReq.flush({ accountName: 'Provider Settlement', balanceMinor: 0 });

    const bankReq = httpMock.expectOne(`${environment.apiBase}/accounts/Platform%20Bank/balance`);
    bankReq.flush({ accountName: 'Platform Bank', balanceMinor: 61248090 });

    const feeReq = httpMock.expectOne(`${environment.apiBase}/accounts/Platform%20Fee%20Revenue/balance`);
    feeReq.flush({ accountName: 'Platform Fee Revenue', balanceMinor: 1776294 });

    expect(result?.length).toBe(5);
    expect(result?.filter((c) => !c.isMocked).length).toBe(3);
    expect(result?.filter((c) => c.isMocked).length).toBe(2);
    expect(result?.find((c) => c.label === 'Platform Bank')?.balanceMinor).toBe(61248090);
  });

  it('getPlatformAccountCards degrades a failed real lookup to a mocked card instead of erroring the whole row', () => {
    let result: PlatformAccountCard[] | undefined;
    service.getPlatformAccountCards().subscribe((r) => (result = r));

    httpMock.expectOne(`${environment.apiBase}/accounts/Provider%20Settlement/balance`).flush('err', {
      status: 500,
      statusText: 'Internal Server Error',
    });
    httpMock
      .expectOne(`${environment.apiBase}/accounts/Platform%20Bank/balance`)
      .flush({ accountName: 'Platform Bank', balanceMinor: 100 });
    httpMock
      .expectOne(`${environment.apiBase}/accounts/Platform%20Fee%20Revenue/balance`)
      .flush({ accountName: 'Platform Fee Revenue', balanceMinor: 100 });

    expect(result?.length).toBe(5);
    expect(result?.find((c) => c.label === 'Provider Settlement')?.isMocked).toBe(true);
  });

  it('getLedgerEntries unwraps PageResponse.content', () => {
    let result: LedgerEntryResponse[] | undefined;
    service.getLedgerEntries().subscribe((r) => (result = r));

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiBase}/operator/ledger/entries` && r.method === 'GET',
    );
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('50');
    req.flush({
      content: [
        {
          id: 'e1',
          transactionRef: 't1',
          debitAccountId: 'a1',
          creditAccountId: 'a2',
          amountMinor: 500,
          postedAt: '2026-01-01T00:00:00Z',
          status: 'POSTED',
        },
      ],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    });

    expect(result?.length).toBe(1);
    expect(result?.[0].status).toBe('POSTED');
  });

  it('getMerchantBalances joins the two real merchant account balances', () => {
    let result: MerchantBalances | undefined;
    service.getMerchantBalances('m1').subscribe((r) => (result = r));

    httpMock
      .expectOne(`${environment.apiBase}/accounts/m1/Merchant%20Payable/balance`)
      .flush({ accountName: 'Merchant Payable', balanceMinor: 4579952 });
    httpMock
      .expectOne(`${environment.apiBase}/accounts/m1/Payout%20In-Transit/balance`)
      .flush({ accountName: 'Payout In-Transit', balanceMinor: 550050 });

    expect(result).toEqual({ payableMinor: 4579952, transitMinor: 550050 });
  });

  it('getMerchantBalances treats a missing account (404, brand-new merchant) as a real $0, not an error', () => {
    let result: MerchantBalances | undefined;
    service.getMerchantBalances('m1').subscribe((r) => (result = r));

    httpMock
      .expectOne(`${environment.apiBase}/accounts/m1/Merchant%20Payable/balance`)
      .flush('not found', { status: 404, statusText: 'Not Found' });
    httpMock
      .expectOne(`${environment.apiBase}/accounts/m1/Payout%20In-Transit/balance`)
      .flush('not found', { status: 404, statusText: 'Not Found' });

    expect(result).toEqual({ payableMinor: 0, transitMinor: 0 });
  });

  it('getLedgerEntriesForTransaction calls GET /ledger with the transaction param', () => {
    let result: LedgerEntryResponse[] | undefined;
    service.getLedgerEntriesForTransaction('txn-1').subscribe((r) => (result = r));

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiBase}/ledger` && r.method === 'GET',
    );
    expect(req.request.params.get('transaction')).toBe('txn-1');
    req.flush([
      {
        id: 'e1',
        transactionRef: 'txn-1',
        debitAccountId: 'a1',
        creditAccountId: 'a2',
        amountMinor: 500,
        postedAt: '2026-01-01T00:00:00Z',
        status: 'POSTED',
      },
    ]);

    expect(result?.length).toBe(1);
  });

  it('getMerchantLedgerEntries calls the real paginated endpoint and unwraps PageResponse.content', () => {
    let result: LedgerEntryResponse[] | undefined;
    service.getMerchantLedgerEntries('m1').subscribe((r) => (result = r));

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiBase}/ledger/merchants/m1/entries` && r.method === 'GET',
    );
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('50');
    req.flush({
      content: [
        {
          id: 'e1',
          transactionRef: 't1',
          debitAccountId: 'a1',
          creditAccountId: 'a2',
          amountMinor: 500,
          postedAt: '2026-01-01T00:00:00Z',
          status: 'POSTED',
        },
      ],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    });

    expect(result?.length).toBe(1);
    expect(result?.[0].transactionRef).toBe('t1');
  });

  it('requestPayout POSTs to /payouts with the merchantId', () => {
    let result: PayoutResponse | undefined;
    service.requestPayout('m1').subscribe((r) => (result = r));

    const req = httpMock.expectOne(`${environment.apiBase}/payouts`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ merchantId: 'm1' });
    req.flush({
      id: 'p1',
      merchantId: 'm1',
      amountMinor: 4579952,
      currency: 'USD',
      status: 'IN_TRANSIT',
      createdAt: '2026-01-01T00:00:00Z',
    });

    expect(result?.status).toBe('IN_TRANSIT');
  });

  it('getPayout calls GET /payouts/{id}', () => {
    let result: PayoutResponse | undefined;
    service.getPayout('p1').subscribe((r) => (result = r));

    const req = httpMock.expectOne(`${environment.apiBase}/payouts/p1`);
    expect(req.request.method).toBe('GET');
    req.flush({
      id: 'p1',
      merchantId: 'm1',
      amountMinor: 4579952,
      currency: 'USD',
      status: 'SETTLED',
      createdAt: '2026-01-01T00:00:00Z',
    });

    expect(result?.status).toBe('SETTLED');
  });
});
