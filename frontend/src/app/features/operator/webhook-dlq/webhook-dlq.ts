import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { WebhookDeliveryResponse } from '../../../core/models';
import { OperatorService } from '../../../core/services/operator.service';
import { initialViewState, ViewState } from '../../../shared/state/view-state';
import { extractErrorMessage } from '../../../shared/utils/http-error';

@Component({
  imports: [DatePipe],
  selector: 'app-webhook-dlq',
  styleUrl: './webhook-dlq.css',
  templateUrl: './webhook-dlq.html',
})
export class WebhookDlq {
  private readonly operatorService = inject(OperatorService);

  private readonly state = signal<ViewState<WebhookDeliveryResponse[]>>(initialViewState());
  protected readonly retryingIds = signal<Set<string>>(new Set());

  protected readonly isLoading = computed(() => this.state().loading);
  protected readonly hasError = computed(() => this.state().error !== null);
  protected readonly errorMessage = computed(() => this.state().error);
  protected readonly deliveries = computed(() => this.state().data ?? []);
  protected readonly isEmpty = computed(
    () => !this.isLoading() && !this.hasError() && this.deliveries().length === 0,
  );
  protected readonly isSuccess = computed(
    () => !this.isLoading() && !this.hasError() && this.deliveries().length > 0,
  );

  protected readonly skeletonRows = Array.from({ length: 5 });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.state.set(initialViewState());
    this.operatorService.listWebhookDlq().subscribe({
      next: (data) => this.state.set({ loading: false, error: null, data }),
      error: (err) =>
        this.state.set({
          loading: false,
          error: extractErrorMessage(err, "Couldn't load dead-lettered deliveries — retry"),
          data: null,
        }),
    });
  }

  protected retry(delivery: WebhookDeliveryResponse): void {
    if (this.retryingIds().has(delivery.id)) return;

    this.retryingIds.update((ids) => new Set(ids).add(delivery.id));
    this.operatorService.retryWebhookDelivery(delivery.id).subscribe({
      next: (updated) => {
        this.state.update((s) => ({
          ...s,
          data: (s.data ?? []).map((d) => (d.id === updated.id ? updated : d)),
        }));
        this.clearRetrying(delivery.id);
      },
      error: () => this.clearRetrying(delivery.id),
    });
  }

  private clearRetrying(id: string): void {
    this.retryingIds.update((ids) => {
      const next = new Set(ids);
      next.delete(id);
      return next;
    });
  }
}
