import { HttpErrorResponse } from '@angular/common/http';
import { ErrorResponse } from '../../core/models';

/**
 * Reads the real backend message off a failed request's body (BE-11/BE-12's uniform
 * ErrorResponse), falling back to the caller's own copy when the body doesn't match
 * that shape — covers network failures, CORS errors, and any non-JSON body gracefully.
 */
export function extractErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse && isErrorResponse(err.error)) {
    return err.error.error;
  }
  return fallback;
}

function isErrorResponse(body: unknown): body is ErrorResponse {
  return typeof body === 'object' && body !== null && typeof (body as { error?: unknown }).error === 'string';
}
