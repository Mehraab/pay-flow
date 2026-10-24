import { Component, computed, inject, signal } from '@angular/core';
import { ProviderConfig } from '../../../core/models';
import { OperatorService } from '../../../core/services/operator.service';
import { initialViewState, ViewState } from '../../../shared/state/view-state';
import { extractErrorMessage } from '../../../shared/utils/http-error';
import { StatusPillVariant } from '../../../shared/utils/payment-status';

const HEALTH_VARIANT_BY_STATUS: Record<string, StatusPillVariant> = {
  HEALTHY: 'captured',
  DEGRADED: 'pending',
};

@Component({
  imports: [],
  selector: 'app-provider-routing',
  styleUrl: './provider-routing.css',
  templateUrl: './provider-routing.html',
})
export class ProviderRouting {
  private readonly operatorService = inject(OperatorService);

  private readonly state = signal<ViewState<ProviderConfig[]>>(initialViewState());
  protected readonly pendingToggleIds = signal<Set<string>>(new Set());
  protected readonly toggleErrors = signal<Record<string, string>>({});

  protected readonly isLoading = computed(() => this.state().loading);
  protected readonly hasError = computed(() => this.state().error !== null);
  protected readonly errorMessage = computed(() => this.state().error);
  protected readonly providers = computed(() => this.state().data ?? []);
  protected readonly isEmpty = computed(
    () => !this.isLoading() && !this.hasError() && this.providers().length === 0,
  );
  protected readonly isSuccess = computed(
    () => !this.isLoading() && !this.hasError() && this.providers().length > 0,
  );

  // Derived client-side from the providers' own active state — no API needed, matches
  // the HTML's existing client-derived routing note.
  protected readonly routingNote = computed(() =>
    this.providers()
      .map((p) => `${p.name}: ${p.active ? 'accepting authorizations' : 'routing disabled'}`)
      .join(' · '),
  );

  protected readonly skeletonCards = Array.from({ length: 2 });

  protected healthVariant(status: string): StatusPillVariant {
    return HEALTH_VARIANT_BY_STATUS[status] ?? 'failed';
  }

  constructor() {
    this.load();
  }

  protected load(): void {
    this.state.set(initialViewState());
    this.operatorService.listProviders().subscribe({
      next: (data) => this.state.set({ loading: false, error: null, data }),
      error: (err) =>
        this.state.set({
          loading: false,
          error: extractErrorMessage(err, "Couldn't load providers — retry"),
          data: null,
        }),
    });
  }

  protected toggleActive(provider: ProviderConfig): void {
    if (this.pendingToggleIds().has(provider.id)) return;

    this.pendingToggleIds.update((ids) => new Set(ids).add(provider.id));
    this.toggleErrors.update(({ [provider.id]: _removed, ...rest }) => rest);

    this.operatorService.updateProviderRouting(provider.id, { active: !provider.active }).subscribe({
      next: (updated) => {
        this.state.update((s) => ({
          ...s,
          data: (s.data ?? []).map((p) => (p.id === updated.id ? updated : p)),
        }));
        this.clearPending(provider.id);
      },
      error: (err) => {
        this.toggleErrors.update((errs) => ({
          ...errs,
          [provider.id]: extractErrorMessage(err, "Couldn't update — try again"),
        }));
        this.clearPending(provider.id);
      },
    });
  }

  private clearPending(id: string): void {
    this.pendingToggleIds.update((ids) => {
      const next = new Set(ids);
      next.delete(id);
      return next;
    });
  }
}
