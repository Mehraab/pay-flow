import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  PageResponse,
  ProviderConfig,
  SagaEventResponse,
  UpdateRoutingRequest,
  WebhookDeliveryResponse,
} from '../models';

@Injectable({ providedIn: 'root' })
export class OperatorService {
  private readonly http = inject(HttpClient);
  private readonly apiBase = environment.apiBase;

  /** REAL: GET /operator/saga-events — one row per Temporal workflow execution. */
  listSagaEvents(): Observable<SagaEventResponse[]> {
    return this.http.get<SagaEventResponse[]>(`${this.apiBase}/operator/saga-events`);
  }

  /** REAL: GET /operator/providers — health/success-rate/latency already on the response (BE-07). */
  listProviders(): Observable<ProviderConfig[]> {
    return this.http.get<ProviderConfig[]>(`${this.apiBase}/operator/providers`);
  }

  /** REAL: PATCH /operator/providers/{id}/routing */
  updateProviderRouting(id: string, patch: UpdateRoutingRequest): Observable<ProviderConfig> {
    return this.http.patch<ProviderConfig>(`${this.apiBase}/operator/providers/${id}/routing`, patch);
  }

  /** REAL: GET /operator/webhooks/dlq */
  listWebhookDlq(): Observable<WebhookDeliveryResponse[]> {
    return this.http.get<WebhookDeliveryResponse[]>(`${this.apiBase}/operator/webhooks/dlq`);
  }

  /** REAL: POST /operator/webhooks/dlq/{id}/retry */
  retryWebhookDelivery(id: string): Observable<WebhookDeliveryResponse> {
    return this.http.post<WebhookDeliveryResponse>(`${this.apiBase}/operator/webhooks/dlq/${id}/retry`, {});
  }

  /**
   * REAL: GET /webhooks/deliveries (BE-08) — merchant-facing, every delivery (not just
   * dead-lettered). Lives here rather than a 5th service: X-Merchant-Id is required by
   * the downstream NotificationController, but the gateway injects it transparently
   * from a ROLE_MERCHANT caller's own JWT, so there's nothing to pass client-side.
   * Placed on OperatorService since that's already the adapter's home for /webhooks/**
   * calls (Task 6's DLQ methods) — the Conventions fix the adapter at exactly four
   * services, so a merchant-facing call living here is the accepted naming tradeoff.
   */
  listWebhookDeliveries(filter: { page?: number; size?: number } = {}): Observable<WebhookDeliveryResponse[]> {
    const params = new HttpParams().set('page', filter.page ?? 0).set('size', filter.size ?? 20);
    return this.http
      .get<PageResponse<WebhookDeliveryResponse>>(`${this.apiBase}/webhooks/deliveries`, { params })
      .pipe(map((res) => res.content));
  }
}
