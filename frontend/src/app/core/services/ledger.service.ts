import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  AccountBalanceResponse,
  LedgerEntryResponse,
  LedgerIntegrityResponse,
  PageResponse,
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
}
