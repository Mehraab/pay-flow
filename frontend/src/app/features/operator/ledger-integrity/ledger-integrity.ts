import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { LedgerEntryResponse, LedgerIntegrityResponse, PageResponse, PlatformAccountCard } from '../../../core/models';
import { LedgerService } from '../../../core/services/ledger.service';
import { Pagination } from '../../../shared/components/pagination/pagination';
import { initialViewState, ViewState } from '../../../shared/state/view-state';
import { extractErrorMessage } from '../../../shared/utils/http-error';
import { formatMoney } from '../../../shared/utils/money';

@Component({
  imports: [DatePipe, Pagination],
  selector: 'app-ledger-integrity',
  styleUrl: './ledger-integrity.css',
  templateUrl: './ledger-integrity.html',
})
export class LedgerIntegrity {
  private readonly ledgerService = inject(LedgerService);

  private readonly integrityState = signal<ViewState<LedgerIntegrityResponse>>(initialViewState());
  private readonly accountsState = signal<ViewState<PlatformAccountCard[]>>(initialViewState());
  private readonly entriesState = signal<ViewState<PageResponse<LedgerEntryResponse>>>(initialViewState());

  protected readonly entriesPage = signal(0); // 0-indexed, matches PageResponse.page

  protected readonly integrityLoading = computed(() => this.integrityState().loading);
  protected readonly integrityError = computed(() => this.integrityState().error);
  protected readonly integrity = computed(() => this.integrityState().data);

  // Per the map's explicit UI-wiring note: compute the delta/icon from the real response —
  // never hardcode "$0.00" + a green check the way the static HTML does.
  protected readonly balanceDeltaMinor = computed(() => {
    const d = this.integrity();
    return d ? d.totalDebitsMinor - d.totalCreditsMinor : null;
  });

  protected readonly accountsLoading = computed(() => this.accountsState().loading);
  protected readonly accountsError = computed(() => this.accountsState().error);
  protected readonly accounts = computed(() => this.accountsState().data ?? []);

  protected readonly entriesLoading = computed(() => this.entriesState().loading);
  protected readonly entriesError = computed(() => this.entriesState().error);
  protected readonly entries = computed(() => this.entriesState().data?.content ?? []);
  protected readonly entriesTotalPages = computed(() => this.entriesState().data?.totalPages ?? 0);
  protected readonly entriesEmpty = computed(
    () => !this.entriesLoading() && !this.entriesError() && this.entries().length === 0,
  );

  protected readonly skeletonRows = Array.from({ length: 4 });
  protected formatMoney = formatMoney;

  constructor() {
    this.loadIntegrity();
    this.loadAccounts();
    this.loadEntries();
  }

  protected loadIntegrity(): void {
    this.integrityState.set(initialViewState());
    this.ledgerService.getLedgerIntegrity().subscribe({
      next: (data) => this.integrityState.set({ loading: false, error: null, data }),
      error: (err) =>
        this.integrityState.set({
          loading: false,
          error: extractErrorMessage(err, "Couldn't load ledger integrity — retry"),
          data: null,
        }),
    });
  }

  protected loadAccounts(): void {
    this.accountsState.set(initialViewState());
    this.ledgerService.getPlatformAccountCards().subscribe({
      next: (data) => this.accountsState.set({ loading: false, error: null, data }),
      error: (err) =>
        this.accountsState.set({
          loading: false,
          error: extractErrorMessage(err, "Couldn't load platform account balances — retry"),
          data: null,
        }),
    });
  }

  protected loadEntries(): void {
    this.entriesState.set(initialViewState());
    this.ledgerService.getLedgerEntries({ page: this.entriesPage() }).subscribe({
      next: (data) => this.entriesState.set({ loading: false, error: null, data }),
      error: (err) =>
        this.entriesState.set({
          loading: false,
          error: extractErrorMessage(err, "Couldn't load the double-entry log — retry"),
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
}
