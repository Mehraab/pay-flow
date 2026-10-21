import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map, switchMap, timer } from 'rxjs';
import { PaymentEvent, PaymentViewEnriched } from '../../../core/models';
import { PaymentService } from '../../../core/services/payment.service';
import { initialViewState, ViewState } from '../../../shared/state/view-state';
import { formatMoney } from '../../../shared/utils/money';
import { statusLabel, statusPillVariant } from '../../../shared/utils/payment-status';

const REFUND_POLL_INTERVAL_MS = 2000;
const REFUND_POLL_MAX_ATTEMPTS = 5;

@Component({
  imports: [DatePipe, RouterLink],
  selector: 'app-transaction-detail',
  styleUrl: './transaction-detail.css',
  templateUrl: './transaction-detail.html',
})
export class TransactionDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly paymentService = inject(PaymentService);

  private id = '';

  private readonly state = signal<ViewState<PaymentViewEnriched>>(initialViewState());
  private readonly timelineState = signal<ViewState<PaymentEvent[]>>(initialViewState());

  protected readonly isRefunding = signal(false);
  protected readonly refundStatusMessage = signal<string | null>(null);

  protected readonly isLoading = computed(() => this.state().loading);
  protected readonly hasError = computed(() => this.state().error !== null);
  protected readonly errorMessage = computed(() => this.state().error);
  protected readonly payment = computed(() => this.state().data);
  protected readonly isSuccess = computed(
    () => !this.isLoading() && !this.hasError() && this.payment() !== null,
  );

  protected readonly timelineLoading = computed(() => this.timelineState().loading);
  protected readonly timelineHasError = computed(() => this.timelineState().error !== null);
  protected readonly timelineErrorMessage = computed(() => this.timelineState().error);
  protected readonly timeline = computed(() => this.timelineState().data ?? []);
  protected readonly timelineEmpty = computed(
    () => !this.timelineLoading() && !this.timelineHasError() && this.timeline().length === 0,
  );

  protected readonly canRefund = computed(() => this.payment()?.status === 'CAPTURED' && !this.isRefunding());

  protected readonly rawEventJson = computed(() => {
    const payment = this.payment();
    if (!payment) return '';
    return JSON.stringify({ event: 'payment.updated', data: payment }, null, 2);
  });

  protected statusLabel = statusLabel;
  protected statusPillVariant = statusPillVariant;
  protected formatMoney = formatMoney;

  constructor() {
    this.route.paramMap
      .pipe(
        map((params) => params.get('id') ?? ''),
        takeUntilDestroyed(),
      )
      .subscribe((id) => {
        if (!id) return;
        this.id = id;
        this.load();
        this.loadTimeline();
      });
  }

  protected load(): void {
    this.state.set(initialViewState());
    this.paymentService.getPayment(this.id).subscribe({
      next: (data) => this.state.set({ loading: false, error: null, data }),
      error: () => this.state.set({ loading: false, error: "Couldn't load this payment — retry", data: null }),
    });
  }

  protected loadTimeline(): void {
    this.timelineState.set(initialViewState());
    this.paymentService.getPaymentEvents(this.id).subscribe({
      next: (data) => this.timelineState.set({ loading: false, error: null, data }),
      error: () =>
        this.timelineState.set({ loading: false, error: "Couldn't load the timeline — retry", data: null }),
    });
  }

  protected refund(): void {
    const payment = this.payment();
    if (!payment || this.isRefunding()) return;
    const previousStatus = payment.status;
    this.isRefunding.set(true);
    this.refundStatusMessage.set('Refund requested — updating…');
    this.paymentService.refundPayment(payment.paymentId).subscribe({
      next: () => this.pollForRefund(previousStatus),
      error: () => {
        this.isRefunding.set(false);
        this.refundStatusMessage.set("Couldn't submit the refund — try again.");
      },
    });
  }

  private pollForRefund(previousStatus: string, attempt = 1): void {
    if (attempt > REFUND_POLL_MAX_ATTEMPTS) {
      this.isRefunding.set(false);
      this.refundStatusMessage.set('Still processing — refresh shortly to check the latest status.');
      return;
    }
    timer(REFUND_POLL_INTERVAL_MS)
      .pipe(switchMap(() => this.paymentService.getPayment(this.id)))
      .subscribe({
        next: (updated) => {
          this.state.set({ loading: false, error: null, data: updated });
          if (updated.status !== previousStatus) {
            this.isRefunding.set(false);
            this.refundStatusMessage.set(null);
          } else {
            this.pollForRefund(previousStatus, attempt + 1);
          }
        },
        error: () => {
          this.isRefunding.set(false);
          this.refundStatusMessage.set("Couldn't confirm the refund status — refresh to check.");
        },
      });
  }
}
