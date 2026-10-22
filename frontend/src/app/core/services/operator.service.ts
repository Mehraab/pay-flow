import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ProviderConfig, SagaEventResponse, UpdateRoutingRequest, WebhookDeliveryResponse } from '../models';

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
}
