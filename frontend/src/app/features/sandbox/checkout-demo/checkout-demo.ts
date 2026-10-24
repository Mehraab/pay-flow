import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { AuthService } from '../../../core/services/auth.service';
import { PaymentService } from '../../../core/services/payment.service';
import { Currency, PaymentViewEnriched } from '../../../core/models';
import { ViewState } from '../../../shared/state/view-state';
import { extractErrorMessage } from '../../../shared/utils/http-error';
import { formatMoney } from '../../../shared/utils/money';
import { statusLabel, statusPillVariant } from '../../../shared/utils/payment-status';

interface CardPreset {
  label: string;
  cardNumber: string;
}

const CARD_PRESETS: CardPreset[] = [
  { label: '4242 — Approved', cardNumber: '4242424242424242' },
  { label: '0002 — Declined (insufficient funds)', cardNumber: '4000000000000002' },
  { label: '0069 — Declined (expired card)', cardNumber: '4000000000000069' },
  { label: '0119 — Processor timeout', cardNumber: '4000000000000119' },
];

const CURRENCIES: Currency[] = ['USD', 'EUR', 'GBP'];

// Real terminal statuses (payflow-payment-query-service vocabulary — see
// shared/utils/payment-status.ts). CREATED/AUTHORIZED are still in-flight.
const TERMINAL_STATUSES = new Set(['CAPTURED', 'FAILED', 'REFUNDED']);

const POLL_INTERVAL_MS = 1500;
const POLL_MAX_ATTEMPTS = 10;

@Component({
  imports: [RouterLink],
  selector: 'app-checkout-demo',
  styleUrl: './checkout-demo.css',
  templateUrl: './checkout-demo.html',
})
export class CheckoutDemo {
  private readonly paymentService = inject(PaymentService);
  private readonly authService = inject(AuthService);

  protected readonly cardPresets = CARD_PRESETS;
  protected readonly currencies = CURRENCIES;

  protected readonly amountMinor = signal(21400);
  protected readonly currency = signal<Currency>('USD');
  protected readonly cardNumber = signal('4242424242424242');
  protected readonly customerName = signal('Sandbox Customer');
  protected readonly merchantId = signal(this.authService.merchantId() ?? '');
  protected readonly forceLedgerFailure = signal(false);

  // Both start idle (loading:false) — unlike every other feature's ViewState, this view
  // has no data to fetch on arrival; it's only ever loading after the user submits.
  private readonly submitState = signal<ViewState<{ paymentId: string }>>({
    loading: false,
    error: null,
    data: null,
  });
  private readonly pollState = signal<ViewState<PaymentViewEnriched>>({
    loading: false,
    error: null,
    data: null,
  });

  protected readonly isSubmitting = computed(() => this.submitState().loading);
  protected readonly isPolling = computed(() => this.pollState().loading);
  protected readonly isBusy = computed(() => this.isSubmitting() || this.isPolling());

  protected readonly submitError = computed(() => this.submitState().error);
  protected readonly pollError = computed(() => this.pollState().error);
  protected readonly errorMessage = computed(() => this.submitError() ?? this.pollError());
  protected readonly hasError = computed(() => this.errorMessage() !== null);

  protected readonly inFlightPayment = computed(() => this.pollState().data);
  protected readonly isSuccess = computed(
    () => !this.isBusy() && !this.hasError() && this.inFlightPayment() !== null,
  );

  protected readonly canSubmit = computed(() => !this.isBusy() && this.merchantId().trim().length > 0);

  protected statusLabel = statusLabel;
  protected statusPillVariant = statusPillVariant;
  protected formatMoney = formatMoney;

  protected selectPreset(preset: CardPreset): void {
    this.cardNumber.set(preset.cardNumber);
  }

  protected submit(): void {
    if (!this.canSubmit()) return;

    this.submitState.set({ loading: true, error: null, data: null });
    this.pollState.set({ loading: false, error: null, data: null });

    this.paymentService
      .createPayment(
        {
          merchantId: this.merchantId().trim(),
          amountMinor: this.amountMinor(),
          currency: this.currency(),
          cardNumber: this.cardNumber(),
          forceLedgerFailure: false,
          customer: this.customerName().trim() || undefined,
        },
        { forceLedgerFailure: this.forceLedgerFailure() },
      )
      .subscribe({
        next: (res) => {
          this.submitState.set({ loading: false, error: null, data: res });
          this.pollState.set({ loading: true, error: null, data: null });
          this.pollSettlement(res.paymentId, 1);
        },
        error: (err) => {
          this.submitState.set({
            loading: false,
            error: extractErrorMessage(err, "Couldn't submit the payment — check the form and try again."),
            data: null,
          });
        },
      });
  }

  private pollSettlement(paymentId: string, attempt: number): void {
    timer(POLL_INTERVAL_MS)
      .pipe(switchMap(() => this.paymentService.getPayment(paymentId)))
      .subscribe({
        next: (payment) => {
          if (TERMINAL_STATUSES.has(payment.status)) {
            this.pollState.set({ loading: false, error: null, data: payment });
            return;
          }
          if (attempt >= POLL_MAX_ATTEMPTS) {
            this.pollState.set({
              loading: false,
              error:
                'Still processing — no final status yet after 15s. This can happen with the 0119 ' +
                'timeout test card; check Transactions shortly for the settled result.',
              data: payment,
            });
            return;
          }
          // Keep narrating the saga's current in-flight status while still polling.
          this.pollState.set({ loading: true, error: null, data: payment });
          this.pollSettlement(paymentId, attempt + 1);
        },
        error: (err) => {
          this.pollState.set({
            loading: false,
            error: extractErrorMessage(err, "Couldn't check the payment's status — view it in Transactions instead."),
            data: null,
          });
        },
      });
  }
}
