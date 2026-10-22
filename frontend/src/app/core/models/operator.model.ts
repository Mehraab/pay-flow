/**
 * BE-07 folded health stats directly onto ProviderConfig rather than shipping a
 * separate health endpoint — there is no ProviderHealth type/call to join client-side.
 */
export interface ProviderConfig {
  id: string;
  name: string;
  priority: number;
  active: boolean;
  healthStatus: string;
  successRatePct: number;
  avgLatencyMs: number;
}

export interface UpdateRoutingRequest {
  priority?: number;
  active?: boolean;
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
