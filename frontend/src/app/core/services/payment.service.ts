import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { CreatePaymentRequest, CreatePaymentResponse, PaymentEvent, PaymentViewEnriched } from '../models';
import {
  PageResponseApi,
  PaymentApiResponse,
  PaymentTimelineEventApi,
  toPaymentEvent,
  toPaymentViewEnriched,
} from './payment-api.mapper';

export interface ListPaymentsFilter {
  status?: string;
  q?: string;
  page?: number;
  size?: number;
}

const DEFAULT_LIST_SIZE = 100;

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly http = inject(HttpClient);
  private readonly apiBase = environment.apiBase;

  /**
   * REAL: GET /api/v1/payments (BE-02). Status/q filtering stays client-side in
   * TransactionsComponent for now (per the plan's explicit "follow-up, not built both
   * ways now" note) — size defaults generously so that client-side filter has the full
   * dev dataset to work with, rather than silently truncating to the backend's own
   * default page size of 10.
   */
  listPayments(filter: ListPaymentsFilter = {}): Observable<PaymentViewEnriched[]> {
    const params = new HttpParams()
      .set('page', filter.page ?? 0)
      .set('size', filter.size ?? DEFAULT_LIST_SIZE);
    return this.http
      .get<PageResponseApi<PaymentApiResponse>>(`${this.apiBase}/api/v1/payments`, { params })
      .pipe(map((res) => res.content.map(toPaymentViewEnriched)));
  }

  /** REAL: GET /api/v1/payments/{id} */
  getPayment(id: string): Observable<PaymentViewEnriched> {
    return this.http
      .get<PaymentApiResponse>(`${this.apiBase}/api/v1/payments/${id}`)
      .pipe(map(toPaymentViewEnriched));
  }

  /** REAL: POST /api/v1/payments/{id}/refund — fire-and-forget, saga-driven async compensation. */
  refundPayment(id: string): Observable<void> {
    return this.http.post<void>(`${this.apiBase}/api/v1/payments/${id}/refund`, {});
  }

  /** REAL: GET /api/v1/payments/{id}/events (BE-05) */
  getPaymentEvents(id: string): Observable<PaymentEvent[]> {
    return this.http
      .get<PaymentTimelineEventApi[]>(`${this.apiBase}/api/v1/payments/${id}/events`)
      .pipe(map((events) => events.map(toPaymentEvent)));
  }

  /**
   * REAL: POST /api/v1/payments — see API_UI_MAP.md's "Card simulator" section: this is
   * the one call normally made server-to-server by the merchant's own backend, exposed
   * here only for the internal Sandbox demo (Task 4). `Idempotency-Key` is required by
   * the command service (missing → 400), so one is generated per call. Per the Catalog's
   * forced-ledger-failure convention, `opts.forceLedgerFailure` is sent as the
   * `X-Force-Ledger-Failure` header — not the body flag — so the demo exercises the same
   * path a real merchant integration would use; the body flag is always left false.
   */
  createPayment(
    req: CreatePaymentRequest,
    opts: { forceLedgerFailure?: boolean } = {},
  ): Observable<CreatePaymentResponse> {
    const headers: Record<string, string> = { 'Idempotency-Key': crypto.randomUUID() };
    if (opts.forceLedgerFailure) {
      headers['X-Force-Ledger-Failure'] = 'true';
    }
    const body: CreatePaymentRequest = { ...req, forceLedgerFailure: false };
    return this.http.post<CreatePaymentResponse>(`${this.apiBase}/api/v1/payments`, body, { headers });
  }
}
