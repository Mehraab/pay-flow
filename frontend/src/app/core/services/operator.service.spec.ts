import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { SagaEventResponse } from '../models';
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
});
