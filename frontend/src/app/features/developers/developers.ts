import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { WebhookDeliveryResponse } from '../../core/models';
import { OperatorService } from '../../core/services/operator.service';
import { initialViewState, ViewState } from '../../shared/state/view-state';
import { StatusPillVariant } from '../../shared/utils/payment-status';

// Real vocabulary: payflow-notification-service's DeliveryStatus (PENDING/DELIVERED/
// FAILED/DEAD_LETTER) — a different enum from payment statuses, so its own mapping.
const DELIVERY_VARIANT_BY_STATUS: Record<string, StatusPillVariant> = {
  DELIVERED: 'captured',
  PENDING: 'pending',
  FAILED: 'failed',
  DEAD_LETTER: 'failed',
};

interface MockApiKey {
  name: string;
  value: string;
  created: string;
  used: string;
}

// No auth-model concept exists for per-merchant API key pairs (BACKEND_TASKS.md's
// "Explicitly deferred" list) — static, clearly-labeled mock data, not wired to any
// service method since there is nothing real to wire to.
const MOCK_API_KEYS: MockApiKey[] = [
  { name: 'Publishable key — Test', value: 'pk_test_51NqY●●●●●●●●●●●●qT3a', created: 'Apr 2, 2025', used: '2 min ago' },
  { name: 'Secret key — Live', value: 'sk_live_51NqY●●●●●●●●●●●●h7Wd', created: 'Jun 18, 2025', used: '3 days ago' },
];

const MOCK_WEBHOOK_URL = 'https://api.northlinegoods.com/webhooks/payflow';

@Component({
  imports: [DatePipe],
  selector: 'app-developers',
  styleUrl: './developers.css',
  templateUrl: './developers.html',
})
export class Developers {
  private readonly operatorService = inject(OperatorService);

  protected readonly mockApiKeys = MOCK_API_KEYS;
  protected readonly mockWebhookUrl = MOCK_WEBHOOK_URL;
  protected readonly urlCopied = signal(false);

  private readonly deliveriesState = signal<ViewState<WebhookDeliveryResponse[]>>(initialViewState());

  protected readonly isLoading = computed(() => this.deliveriesState().loading);
  protected readonly hasError = computed(() => this.deliveriesState().error !== null);
  protected readonly errorMessage = computed(() => this.deliveriesState().error);
  protected readonly deliveries = computed(() => this.deliveriesState().data ?? []);
  protected readonly isEmpty = computed(
    () => !this.isLoading() && !this.hasError() && this.deliveries().length === 0,
  );
  protected readonly isSuccess = computed(
    () => !this.isLoading() && !this.hasError() && this.deliveries().length > 0,
  );

  protected readonly skeletonRows = Array.from({ length: 5 });
  protected statusPillVariant = (status: string): StatusPillVariant => DELIVERY_VARIANT_BY_STATUS[status] ?? 'pending';

  constructor() {
    this.load();
  }

  protected load(): void {
    this.deliveriesState.set(initialViewState());
    this.operatorService.listWebhookDeliveries().subscribe({
      next: (data) => this.deliveriesState.set({ loading: false, error: null, data }),
      error: () =>
        this.deliveriesState.set({
          loading: false,
          error: "Couldn't load webhook deliveries — retry",
          data: null,
        }),
    });
  }

  protected copyWebhookUrl(): void {
    navigator.clipboard?.writeText(this.mockWebhookUrl).then(() => {
      this.urlCopied.set(true);
      setTimeout(() => this.urlCopied.set(false), 2000);
    });
  }

  protected copyKey(key: MockApiKey): void {
    navigator.clipboard?.writeText(key.value);
  }
}
