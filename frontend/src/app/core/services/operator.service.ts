import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SagaEventResponse } from '../models';

@Injectable({ providedIn: 'root' })
export class OperatorService {
  private readonly http = inject(HttpClient);
  private readonly apiBase = environment.apiBase;

  /** REAL: GET /operator/saga-events — one row per Temporal workflow execution. */
  listSagaEvents(): Observable<SagaEventResponse[]> {
    return this.http.get<SagaEventResponse[]>(`${this.apiBase}/operator/saga-events`);
  }

  // Remaining methods added in Task 6 as each feature needs them.
}
