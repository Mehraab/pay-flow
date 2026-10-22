import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { SagaEventResponse } from '../../../core/models';
import { OperatorService } from '../../../core/services/operator.service';
import { initialViewState, ViewState } from '../../../shared/state/view-state';
import { formatDuration } from '../../../shared/utils/duration';
import { StatusPillVariant } from '../../../shared/utils/payment-status';

// Temporal's WorkflowExecutionStatus, as mapped by SagaEventsService.mapStatus — a
// different vocabulary from payment statuses, so it gets its own pill mapping.
const SAGA_VARIANT_BY_STATUS: Record<string, StatusPillVariant> = {
  COMPLETED: 'captured',
  RUNNING: 'pending',
  FAILED: 'failed',
  TERMINATED: 'failed',
  TIMED_OUT: 'failed',
  CANCELED: 'failed',
  CONTINUED_AS_NEW: 'pending',
};

@Component({
  imports: [DatePipe],
  selector: 'app-saga-events',
  styleUrl: './saga-events.css',
  templateUrl: './saga-events.html',
})
export class SagaEvents {
  private readonly operatorService = inject(OperatorService);

  private readonly state = signal<ViewState<SagaEventResponse[]>>(initialViewState());

  protected readonly isLoading = computed(() => this.state().loading);
  protected readonly hasError = computed(() => this.state().error !== null);
  protected readonly errorMessage = computed(() => this.state().error);
  protected readonly events = computed(() => this.state().data ?? []);
  protected readonly isEmpty = computed(
    () => !this.isLoading() && !this.hasError() && this.events().length === 0,
  );
  protected readonly isSuccess = computed(
    () => !this.isLoading() && !this.hasError() && this.events().length > 0,
  );

  protected readonly skeletonRows = Array.from({ length: 6 });
  protected formatDuration = formatDuration;
  protected statusPillVariant = (status: string): StatusPillVariant => SAGA_VARIANT_BY_STATUS[status] ?? 'pending';

  constructor() {
    this.load();
  }

  protected load(): void {
    this.state.set(initialViewState());
    this.operatorService.listSagaEvents().subscribe({
      next: (data) => this.state.set({ loading: false, error: null, data }),
      error: () =>
        this.state.set({ loading: false, error: "Couldn't load saga events — retry", data: null }),
    });
  }
}
