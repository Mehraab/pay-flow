import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { LedgerEntryResponse, PlatformAccountCard } from '../models';
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
});
