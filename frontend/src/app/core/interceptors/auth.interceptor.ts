import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../services/auth.service';

/** Marks a request as already-retried-once, so a bad refresh token can't loop forever. */
export const IS_RETRY = new HttpContextToken<boolean>(() => false);

const AUTH_ENDPOINTS = ['/auth/token', '/auth/refresh', '/auth/revoke'].map(
  (path) => `${environment.apiBase}${path}`,
);

function isAuthEndpoint(url: string): boolean {
  return AUTH_ENDPOINTS.some((endpoint) => url.startsWith(endpoint));
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);

  // /auth/** is public and handles its own token lifecycle (AuthService.refreshAccessToken
  // owns the 401-from-refresh case) — never attach a bearer token or retry these.
  if (isAuthEndpoint(req.url)) {
    return next(req);
  }

  const isApiRequest = req.url.startsWith(environment.apiBase);
  const token = authService.accessToken();
  const authorizedReq =
    isApiRequest && token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authorizedReq).pipe(
    catchError((error: unknown) => {
      const isRetry = authorizedReq.context.get(IS_RETRY);
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || !isApiRequest || isRetry) {
        return throwError(() => error);
      }

      // refreshAccessToken() handles a failed refresh itself (revokeAndLogout + rethrow);
      // that error simply propagates here unchanged.
      return authService.refreshAccessToken().pipe(
        switchMap((tokenResponse) => {
          const retryReq = authorizedReq.clone({
            setHeaders: { Authorization: `Bearer ${tokenResponse.accessToken}` },
            context: authorizedReq.context.set(IS_RETRY, true),
          });
          return next(retryReq);
        }),
      );
    }),
  );
};
