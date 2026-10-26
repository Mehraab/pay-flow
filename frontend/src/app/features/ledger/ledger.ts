import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { LedgerEntryResponse, MerchantBalances, PageResponse, PayoutResponse } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { LedgerService } from '../../core/services/ledger.service';
import { Pagination } from '../../shared/components/pagination/pagination';
import { initialViewState, ViewState } from '../../shared/state/view-state';
import { extractErrorMessage } from '../../shared/utils/http-error';
import { formatMoney } from '../../shared/utils/money';

const PAYOUT_POLL_INTERVAL_MS = 2000;
const PAYOUT_POLL_MAX_ATTEMPTS = 5;
const TERMINAL_PAYOUT_STATUSES = new Set(['SETTLED', 'FAILED']);
const NO_MERCHANT_ID_MESSAGE = 'No merchant ID on this session — sign in with a merchant ID to see your ledger.';

@Component({
  imports: [DatePipe, Pagination],
  selector: 'app-ledger',
  styleUrl: './ledger.css',
  templateUrl: './ledger.html',
})
export class Ledger {
  private readonly ledgerService = inject(LedgerService);
  private readonly authService = inject(AuthService);

  private readonly merchantId = computed(() => this.authService.merchantId() ?? '');

  private readonly balancesState = signal<ViewState<MerchantBalances>>(initialViewState());
  private readonly entriesState = signal<ViewState<PageResponse<LedgerEntryResponse>>>(initialViewState());
  private readonly payoutState = signal<ViewState<PayoutResponse>>({ loading: false, error: null, data: null });

  protected readonly entriesPage = signal(0); // 0-indexed, matches PageResponse.page

  protected readonly balancesLoading = computed(() => this.balancesState().loading);
  protected readonly balancesError = computed(() => this.balancesState().error);
  protected readonly balances = computed(() => this.balancesState().data);

  protected readonly entriesLoading = computed(() => this.entriesState().loading);
  protected readonly entriesError = computed(() => this.entriesState().error);
  protected readonly entries = computed(() => this.entriesState().data?.content ?? []);
  protected readonly entriesTotalPages = computed(() => this.entriesState().data?.totalPages ?? 0);
  protected readonly entriesEmpty = computed(
    () => !this.entriesLoading() && !this.entriesError() && this.entries().length === 0,
  );

  protected readonly payoutLoading = computed(() => this.payoutState().loading);
  protected readonly payoutError = computed(() => this.payoutState().error);
  protected readonly payout = computed(() => this.payoutState().data);

  protected readonly skeletonRows = Array.from({ length: 4 });
  protected formatMoney = formatMoney;

  constructor() {
    if (!this.merchantId()) {
      this.balancesState.set({ loading: false, error: NO_MERCHANT_ID_MESSAGE, data: null });
      this.entriesState.set({ loading: false, error: NO_MERCHANT_ID_MESSAGE, data: null });
      return;
    }
    this.loadBalances();
    this.loadEntries();
  }

  protected loadBalances(): void {
    const merchantId = this.merchantId();
    if (!merchantId) return;
    this.balancesState.set(initialViewState());
    this.ledgerService.getMerchantBalances(merchantId).subscribe({
      next: (data) => this.balancesState.set({ loading: false, error: null, data }),
      error: (err) =>
        this.balancesState.set({
          loading: false,
          error: extractErrorMessage(err, "Couldn't load balances — retry"),
          data: null,
        }),
    });
  }

  protected loadEntries(): void {
    const merchantId = this.merchantId();
    if (!merchantId) return;
    this.entriesState.set(initialViewState());
    this.ledgerService.getMerchantLedgerEntries(merchantId, { page: this.entriesPage() }).subscribe({
      next: (data) => this.entriesState.set({ loading: false, error: null, data }),
      error: (err) =>
        this.entriesState.set({
          loading: false,
          error: extractErrorMessage(err, "Couldn't load the ledger log — retry"),
          data: null,
        }),
    });
  }

  // Explicit re-fetch on click, not an effect() watching entriesPage — keeps the same
  // "no signal writes inside effect()" discipline the app has held since Task 1.
  protected onEntriesPageChange(page: number): void {
    this.entriesPage.set(page);
    this.loadEntries();
  }

  protected requestPayout(): void {
    const merchantId = this.merchantId();
    if (!merchantId || this.payoutLoading()) return;

    this.payoutState.set({ loading: true, error: null, data: null });
    this.ledgerService.requestPayout(merchantId).subscribe({
      next: (payout) => {
        this.payoutState.set({ loading: true, error: null, data: payout });
        this.pollPayout(payout.id, 1);
      },
      error: (err) =>
        this.payoutState.set({
          loading: false,
          error: extractErrorMessage(err, "Couldn't request a payout — try again."),
          data: null,
        }),
    });
  }

  private pollPayout(id: string, attempt: number): void {
    timer(PAYOUT_POLL_INTERVAL_MS)
      .pipe(switchMap(() => this.ledgerService.getPayout(id)))
      .subscribe({
        next: (payout) => {
          if (TERMINAL_PAYOUT_STATUSES.has(payout.status)) {
            this.payoutState.set({ loading: false, error: null, data: payout });
            this.loadBalances();
            return;
          }
          if (attempt >= PAYOUT_POLL_MAX_ATTEMPTS) {
            this.payoutState.set({
              loading: false,
              error: 'Still in-transit — check back shortly for the settled result.',
              data: payout,
            });
            return;
          }
          this.payoutState.set({ loading: true, error: null, data: payout });
          this.pollPayout(id, attempt + 1);
        },
        error: (err) =>
          this.payoutState.set({
            loading: false,
            error: extractErrorMessage(err, "Couldn't check the payout's status — refresh to check."),
            data: null,
          }),
      });
  }
}
