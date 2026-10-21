import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { PaymentViewEnriched } from '../models';
import { PaymentService } from './payment.service';

describe('PaymentService', () => {
  let service: PaymentService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PaymentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listPayments calls the real paginated endpoint and unwraps + maps content', () => {
    let result: PaymentViewEnriched[] | undefined;
    service.listPayments().subscribe((r) => (result = r));

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiBase}/api/v1/payments` && r.method === 'GET',
    );
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('100');

    req.flush({
      content: [
        {
          paymentId: 'p1',
          merchantId: 'm1',
          amountMinor: 500,
          currency: 'USD',
          status: 'CAPTURED',
          providerRef: 'pr_1',
          declineReason: null,
          createdAt: '2026-01-01T00:00:00Z',
          customer: 'Ava',
          paymentMethod: { brand: 'Visa', last4: '4242' },
          idempotencyKey: 'idem_1',
          providerDisplayName: 'Mock Simulator',
        },
      ],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });

    expect(result?.length).toBe(1);
    expect(result?.[0].customerName).toBe('Ava');
    expect(result?.[0].method).toBe('Visa •••• 4242');
  });

  it('getPayment maps the raw response onto PaymentViewEnriched', () => {
    let result: PaymentViewEnriched | undefined;
    service.getPayment('p1').subscribe((r) => (result = r));

    const req = httpMock.expectOne(`${environment.apiBase}/api/v1/payments/p1`);
    expect(req.request.method).toBe('GET');
    req.flush({
      paymentId: 'p1',
      merchantId: 'm1',
      amountMinor: 500,
      currency: 'USD',
      status: 'CAPTURED',
      providerRef: 'pr_1',
      declineReason: null,
      createdAt: '2026-01-01T00:00:00Z',
      customer: 'Ava',
      paymentMethod: { brand: 'Visa', last4: '4242' },
      idempotencyKey: 'idem_1',
      providerDisplayName: 'Mock Simulator',
    });

    expect(result?.customerName).toBe('Ava');
    expect(result?.method).toBe('Visa •••• 4242');
  });

  it('getPaymentEvents maps eventType/description onto label/note', () => {
    let result: { label: string; status: string; note?: string }[] | undefined;
    service.getPaymentEvents('p1').subscribe((r) => (result = r));

    const req = httpMock.expectOne(`${environment.apiBase}/api/v1/payments/p1/events`);
    expect(req.request.method).toBe('GET');
    req.flush([
      { eventType: 'PAYMENT_CREATED', status: 'CREATED', timestamp: '2026-01-01T00:00:00Z', description: 'x' },
      {
        eventType: 'PAYMENT_FAILED',
        status: 'FAILED',
        timestamp: '2026-01-01T00:00:01Z',
        description: 'Declined: bad',
      },
    ]);

    expect(result?.[0].label).toBe('Payment created');
    expect(result?.[0].status).toBe('done');
    expect(result?.[1].label).toBe('Declined');
    expect(result?.[1].status).toBe('failed');
    expect(result?.[1].note).toBe('Declined: bad');
  });

  it('refundPayment posts to the refund endpoint with no body', () => {
    service.refundPayment('p1').subscribe();

    const req = httpMock.expectOne(`${environment.apiBase}/api/v1/payments/p1/refund`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });
});
