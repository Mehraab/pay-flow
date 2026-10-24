/** Platform-wide uniform error body (BE-11/BE-12) — payflow-common's ErrorResponse.java. */
export interface ErrorResponse {
  error: string;
  status: number;
  timestamp: string;
}
