import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { PaymentViewEnriched, PaymentsSummary } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { PaymentService } from '../../core/services/payment.service';
import { initialViewState, ViewState } from '../../shared/state/view-state';
import { extractErrorMessage } from '../../shared/utils/http-error';
import { formatMoney } from '../../shared/utils/money';
import { statusLabel, statusPillVariant } from '../../shared/utils/payment-status';
import { buildSparkline } from '../../shared/utils/sparkline';

const SUMMARY_DAYS = 30;

@Component({
  imports: [],
  selector: 'app-overview',
  styleUrl: './overview.css',
  templateUrl: './overview.html',
})
export class Overview {
  private readonly paymentService = inject(PaymentService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  // /transactions/:id is merchantGuard-only (Task 3) — an operator viewing this shared
  // Overview route would get bounced to /signin by a row click, so rows are informational
  // (not links) for operators rather than pointing somewhere they can't actually open.
  protected readonly rowsClickable = computed(() => this.authService.role() === 'MERCHANT');

  private readonly summaryState = signal<ViewState<PaymentsSummary>>(initialViewState());
  private readonly recentState = signal<ViewState<PaymentViewEnriched[]>>(initialViewState());

  protected readonly summaryLoading = computed(() => this.summaryState().loading);
  protected readonly summaryError = computed(() => this.summaryState().error);
  protected readonly summary = computed(() => this.summaryState().data);
  protected readonly summarySuccess = computed(
    () => !this.summaryLoading() && !this.summaryError() && this.summary() !== null,
  );

  protected readonly recentLoading = computed(() => this.recentState().loading);
  protected readonly recentError = computed(() => this.recentState().error);
  protected readonly recent = computed(() => this.recentState().data ?? []);
  protected readonly recentEmpty = computed(
    () => !this.recentLoading() && !this.recentError() && this.recent().length === 0,
  );
  protected readonly recentSuccess = computed(
    () => !this.recentLoading() && !this.recentError() && this.recent().length > 0,
  );

  protected readonly chart = computed(() => buildSparkline(this.summary()?.dailyVolume ?? []));

  protected readonly summaryDays = SUMMARY_DAYS;
  protected readonly skeletonTiles = Array.from({ length: 3 });
  protected readonly skeletonRows = Array.from({ length: 6 });
  protected readonly Math = Math;

  protected formatMoney = formatMoney;
  protected statusLabel = statusLabel;
  protected statusPillVariant = statusPillVariant;

  constructor() {
    this.loadSummary();
    this.loadRecent();
  }

  protected loadSummary(): void {
    this.summaryState.set(initialViewState());
    this.paymentService.getSummary(SUMMARY_DAYS).subscribe({
      next: (data) => this.summaryState.set({ loading: false, error: null, data }),
      error: (err) =>
        this.summaryState.set({
          loading: false,
          error: extractErrorMessage(err, 'Analytics unavailable'),
          data: null,
        }),
    });
  }

  protected loadRecent(): void {
    this.recentState.set(initialViewState());
    this.paymentService.listPayments({ page: 0, size: 6 }).subscribe({
      next: (data) => this.recentState.set({ loading: false, error: null, data }),
      error: (err) =>
        this.recentState.set({
          loading: false,
          error: extractErrorMessage(err, "Couldn't load recent transactions — retry"),
          data: null,
        }),
    });
  }

  protected openPayment(id: string): void {
    this.router.navigate(['/transactions', id]);
  }

  protected formatAuthTime(seconds: number): string {
    return seconds < 1 ? `${Math.round(seconds * 1000)}ms` : `${seconds.toFixed(2)}s`;
  }
}
