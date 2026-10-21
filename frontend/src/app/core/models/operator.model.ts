export interface ProviderConfig {
  id: string;
  name: string;
  priority: number;
  active: boolean;
}

export interface UpdateRoutingRequest {
  priority?: number;
  active?: boolean;
}

/** STUB(BE-07) — no real endpoint exists yet. */
export interface ProviderHealth {
  providerId: string;
  successRatePct: number;
  avgLatencyMs: number;
}

export interface SagaEventResponse {
  workflowId: string;
  status: string;
  startTime: string;
  closeTime: string | null;
}

export interface WebhookDeliveryResponse {
  id: string;
  endpointId: string;
  eventType: string;
  status: string;
  attemptCount: number;
  responseCode: number;
  lastAttemptAt: string;
  nextRetryAt: string | null;
}
