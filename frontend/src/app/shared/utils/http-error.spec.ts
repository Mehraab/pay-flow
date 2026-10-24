import { HttpErrorResponse } from '@angular/common/http';
import { extractErrorMessage } from './http-error';

describe('extractErrorMessage', () => {
  it('returns the real backend message when the error body matches ErrorResponse', () => {
    const err = new HttpErrorResponse({
      error: { error: 'No available balance for payout', status: 409, timestamp: '2026-01-01T00:00:00Z' },
      status: 409,
    });
    expect(extractErrorMessage(err, 'fallback')).toBe('No available balance for payout');
  });

  it('falls back when the error is not an HttpErrorResponse at all', () => {
    expect(extractErrorMessage(new Error('boom'), 'fallback')).toBe('fallback');
    expect(extractErrorMessage('boom', 'fallback')).toBe('fallback');
    expect(extractErrorMessage(undefined, 'fallback')).toBe('fallback');
  });

  it('falls back on a network/CORS failure where error body is a ProgressEvent, not JSON', () => {
    const err = new HttpErrorResponse({ error: new ProgressEvent('error'), status: 0 });
    expect(extractErrorMessage(err, 'fallback')).toBe('fallback');
  });

  it('falls back when the body is JSON but missing the error field', () => {
    const err = new HttpErrorResponse({ error: { message: 'something else' }, status: 500 });
    expect(extractErrorMessage(err, 'fallback')).toBe('fallback');
  });

  it('falls back when the body is null (e.g. a 204 or empty error body)', () => {
    const err = new HttpErrorResponse({ error: null, status: 500 });
    expect(extractErrorMessage(err, 'fallback')).toBe('fallback');
  });
});
