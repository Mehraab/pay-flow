import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { PaymentViewEnriched } from '../../core/models';
import { PaymentService } from '../../core/services/payment.service';
import { SearchQueryService } from '../../shared/services/search-query.service';
import { initialViewState, ViewState } from '../../shared/state/view-state';
import { statusLabel, statusPillVariant } from '../../shared/utils/payment-status';
import { formatMoney } from '../../shared/utils/money';
import { applyFilters, StatusFilter } from './apply-filters';

interface StatusChip {
  value: StatusFilter;
  label: string;
}

const STATUS_CHIPS: StatusChip[] = [
  { value: 'all', label: 'All' },
  { value: 'captured', label: 'Captured' },
  { value: 'pending', label: 'Pending' },
  { value: 'failed', label: 'Failed' },
  { value: 'refunded', label: 'Refunded' },
];

@Component({
  imports: [DatePipe],
  selector: 'app-transactions',
  styleUrl: './transactions.css',
  templateUrl: './transactions.html',
})
export class Transactions {
  private readonly paymentService = inject(PaymentService);
  private readonly router = inject(Router);
  protected readonly searchQueryService = inject(SearchQueryService);

  protected readonly chips = STATUS_CHIPS;
  protected readonly statusFilter = signal<StatusFilter>('all');
  protected readonly searchQuery = this.searchQueryService.query;

  private readonly state = signal<ViewState<PaymentViewEnriched[]>>(initialViewState());

  protected readonly isLoading = computed(() => this.state().loading);
  protected readonly hasError = computed(() => this.state().error !== null);
  protected readonly errorMessage = computed(() => this.state().error);

  protected readonly filtered = computed(() =>
    applyFilters(this.state().data ?? [], this.statusFilter(), this.searchQuery()),
  );

  protected readonly isEmpty = computed(
    () => !this.isLoading() && !this.hasError() && this.filtered().length === 0,
  );
  protected readonly isSuccess = computed(
    () => !this.isLoading() && !this.hasError() && this.filtered().length > 0,
  );
  protected readonly isUnfiltered = computed(() => this.statusFilter() === 'all' && !this.searchQuery());

  protected readonly skeletonRows = Array.from({ length: 6 });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.state.set(initialViewState());
    this.paymentService.listPayments().subscribe({
      next: (data) => this.state.set({ loading: false, error: null, data }),
      error: () =>
        this.state.set({ loading: false, error: "Couldn't load transactions — retry", data: null }),
    });
  }

  protected setStatusFilter(filter: StatusFilter): void {
    this.statusFilter.set(filter);
  }

  protected openPayment(id: string): void {
    this.router.navigate(['/transactions', id]);
  }

  protected statusLabel = statusLabel;
  protected statusPillVariant = statusPillVariant;
  protected formatMoney = formatMoney;
}
