import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  AccountBalanceResponse,
  LedgerEntryResponse,
  LedgerIntegrityResponse,
  MerchantBalances,
  PageResponse,
  PayoutResponse,
  PlatformAccountCard,
} from '../models';

export interface ListLedgerEntriesFilter {
  since?: string;
  page?: number;
  size?: number;
}

const DEFAULT_ENTRIES_SIZE = 50;

// Real platform accounts seeded by PlatformAccountSeeder.java, with the AccountType each
// was seeded as — reachable via GET /accounts/{accountName}/balance (not operator-gated,
// but only ever called from here).
const REAL_PLATFORM_ACCOUNTS: { accountName: string; kind: PlatformAccountCard['kind'] }[] = [
  { accountName: 'Provider Settlement', kind: 'Asset' },
  { accountName: 'Platform Bank', kind: 'Asset' },
  { accountName: 'Platform Fee Revenue', kind: 'Revenue' },
];

// Merchant-scoped-only accounts (LedgerAccountService.MERCHANT_PAYABLE/PAYOUT_IN_TRANSIT) —
// no all-merchants aggregate endpoint exists for either, so these stay mocked.
const MOCKED_PLATFORM_CARDS: PlatformAccountCard[] = [
  { label: 'Merchant payable (all-merchants)', kind: 'Liability', balanceMinor: 4579952, isMocked: true },
  { label: 'Payout in-transit (all-merchants)', kind: 'Liability', balanceMinor: 0, isMocked: true },
];

// Exact account names seeded by LedgerAccountService.MERCHANT_PAYABLE / PAYOUT_IN_TRANSIT.
const MERCHANT_PAYABLE_ACCOUNT = 'Merchant Payable';
const PAYOUT_IN_TRANSIT_ACCOUNT = 'Payout In-Transit';

@Injectable({ providedIn: 'root' })
export class LedgerService {
  private readonly http = inject(HttpClient);
  private readonly apiBase = environment.apiBase;

  /** REAL: GET /operator/ledger/integrity */
  getLedgerIntegrity(): Observable<LedgerIntegrityResponse> {
    return this.http.get<LedgerIntegrityResponse>(`${this.apiBase}/operator/ledger/integrity`);
  }

  /**
   * Three of the five cards are REAL (GET /accounts/{accountName}/balance); the other
   * two have no backing endpoint at all (see PlatformAccountCard's doc) and stay mocked.
   * A failed real lookup degrades to a mocked $0 card rather than failing the whole row —
   * one missing account shouldn't block the other four from rendering.
   */
  getPlatformAccountCards(): Observable<PlatformAccountCard[]> {
    const realCards$ = REAL_PLATFORM_ACCOUNTS.map(({ accountName, kind }) =>
      this.http
        .get<AccountBalanceResponse>(`${this.apiBase}/accounts/${encodeURIComponent(accountName)}/balance`)
        .pipe(
          map((res): PlatformAccountCard => ({ label: res.accountName, kind, balanceMinor: res.balanceMinor, isMocked: false })),
          catchError(() => of<PlatformAccountCard>({ label: accountName, kind, balanceMinor: 0, isMocked: true })),
        ),
    );
    return forkJoin(realCards$).pipe(map((cards) => [...cards, ...MOCKED_PLATFORM_CARDS]));
  }

  /** REAL: GET /operator/ledger/entries (BE-06) — the platform-wide double-entry log. */
  getLedgerEntries(filter: ListLedgerEntriesFilter = {}): Observable<LedgerEntryResponse[]> {
    let params = new HttpParams().set('page', filter.page ?? 0).set('size', filter.size ?? DEFAULT_ENTRIES_SIZE);
    if (filter.since) {
      params = params.set('since', filter.since);
    }
    return this.http
      .get<PageResponse<LedgerEntryResponse>>(`${this.apiBase}/operator/ledger/entries`, { params })
      .pipe(map((res) => res.content));
  }

  /**
   * REAL: GET /accounts/{merchantId}/{accountName}/balance, joined for both accounts.
   * A brand-new merchant with no activity yet legitimately has no account row created
   * (404) — that's a real $0 balance, not an error, so it degrades per-account rather
   * than failing the whole card pair.
   */
  getMerchantBalances(merchantId: string): Observable<MerchantBalances> {
    return forkJoin([
      this.fetchMerchantAccountBalance(merchantId, MERCHANT_PAYABLE_ACCOUNT),
      this.fetchMerchantAccountBalance(merchantId, PAYOUT_IN_TRANSIT_ACCOUNT),
    ]).pipe(map(([payableMinor, transitMinor]) => ({ payableMinor, transitMinor })));
  }

  private fetchMerchantAccountBalance(merchantId: string, accountName: string): Observable<number> {
    return this.http
      .get<AccountBalanceResponse>(
        `${this.apiBase}/accounts/${merchantId}/${encodeURIComponent(accountName)}/balance`,
      )
      .pipe(
        map((res) => res.balanceMinor),
        catchError(() => of(0)),
      );
  }

  /** REAL: GET /ledger?transaction={ref} — entries for one transaction. */
  getLedgerEntriesForTransaction(transactionRef: string): Observable<LedgerEntryResponse[]> {
    const params = new HttpParams().set('transaction', transactionRef);
    return this.http.get<LedgerEntryResponse[]>(`${this.apiBase}/ledger`, { params });
  }

  /**
   * REAL: GET /ledger/merchants/{merchantId}/entries (BE-09) — merchant-wide ledger
   * feed across multiple transactions (unlike GET /ledger, which is scoped to one
   * transactionRef). Paginated; unwraps PageResponse.content like the other paginated
   * calls.
   */
  getMerchantLedgerEntries(merchantId: string, filter: ListLedgerEntriesFilter = {}): Observable<LedgerEntryResponse[]> {
    const params = new HttpParams()
      .set('page', filter.page ?? 0)
      .set('size', filter.size ?? DEFAULT_ENTRIES_SIZE);
    return this.http
      .get<PageResponse<LedgerEntryResponse>>(`${this.apiBase}/ledger/merchants/${merchantId}/entries`, { params })
      .pipe(map((res) => res.content));
  }

  /** REAL: POST /payouts */
  requestPayout(merchantId: string): Observable<PayoutResponse> {
    return this.http.post<PayoutResponse>(`${this.apiBase}/payouts`, { merchantId });
  }

  /** REAL: GET /payouts/{id} — used to poll a just-requested payout's status. */
  getPayout(id: string): Observable<PayoutResponse> {
    return this.http.get<PayoutResponse>(`${this.apiBase}/payouts/${id}`);
  }
}
