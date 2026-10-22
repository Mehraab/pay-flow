import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { ProviderConfig, SagaEventResponse, WebhookDeliveryResponse } from '../models';
import { OperatorService } from './operator.service';

describe('OperatorService', () => {
  let service: OperatorService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OperatorService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listSagaEvents calls the real endpoint and returns the array as-is', () => {
    let result: SagaEventResponse[] | undefined;
    service.listSagaEvents().subscribe((r) => (result = r));

    const req = httpMock.expectOne(`${environment.apiBase}/operator/saga-events`);
    expect(req.request.method).toBe('GET');
    req.flush([
      { workflowId: 'wf-1', status: 'COMPLETED', startTime: '2026-01-01T00:00:00Z', closeTime: '2026-01-01T00:00:05Z' },
    ]);

    expect(result?.length).toBe(1);
    expect(result?.[0].workflowId).toBe('wf-1');
  });

  it('listProviders calls the real endpoint, including the BE-07 health fields', () => {
    let result: ProviderConfig[] | undefined;
    service.listProviders().subscribe((r) => (result = r));

    const req = httpMock.expectOne(`${environment.apiBase}/operator/providers`);
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        id: 'p1',
        name: 'Provider A',
        priority: 1,
        active: true,
        healthStatus: 'HEALTHY',
        successRatePct: 98.4,
        avgLatencyMs: 210,
      },
    ]);

    expect(result?.[0].healthStatus).toBe('HEALTHY');
    expect(result?.[0].successRatePct).toBe(98.4);
  });

  it('updateProviderRouting PATCHes the routing endpoint with the partial update', () => {
    let result: ProviderConfig | undefined;
    service.updateProviderRouting('p1', { active: false }).subscribe((r) => (result = r));

    const req = httpMock.expectOne(`${environment.apiBase}/operator/providers/p1/routing`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ active: false });
    req.flush({
      id: 'p1',
      name: 'Provider A',
      priority: 1,
      active: false,
      healthStatus: 'HEALTHY',
      successRatePct: 98.4,
      avgLatencyMs: 210,
    });

    expect(result?.active).toBe(false);
  });

  it('listWebhookDlq calls the real endpoint and returns the array as-is', () => {
    let result: WebhookDeliveryResponse[] | undefined;
    service.listWebhookDlq().subscribe((r) => (result = r));

    const req = httpMock.expectOne(`${environment.apiBase}/operator/webhooks/dlq`);
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        id: 'd1',
        endpointId: 'e1',
        eventType: 'payment.captured',
        status: 'FAILED',
        attemptCount: 5,
        responseCode: 500,
        lastAttemptAt: '2026-01-01T00:00:00Z',
        nextRetryAt: '2026-01-01T00:05:00Z',
      },
    ]);

    expect(result?.length).toBe(1);
    expect(result?.[0].attemptCount).toBe(5);
  });

  it('retryWebhookDelivery POSTs to the retry endpoint', () => {
    let result: WebhookDeliveryResponse | undefined;
    service.retryWebhookDelivery('d1').subscribe((r) => (result = r));

    const req = httpMock.expectOne(`${environment.apiBase}/operator/webhooks/dlq/d1/retry`);
    expect(req.request.method).toBe('POST');
    req.flush({
      id: 'd1',
      endpointId: 'e1',
      eventType: 'payment.captured',
      status: 'DELIVERED',
      attemptCount: 6,
      responseCode: 200,
      lastAttemptAt: '2026-01-01T00:06:00Z',
      nextRetryAt: null,
    });

    expect(result?.status).toBe('DELIVERED');
  });
});
