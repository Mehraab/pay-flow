import { HttpClient } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, shareReplay, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { RefreshTokenRequest, Role, TokenRequest, TokenResponse } from '../models';
import { decodeJwtPayload } from '../../shared/utils/jwt';

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  role: Role | null;
  merchantId: string | null;
  expiresAt: number | null;
}

/**
 * TokenResponse never echoes merchantId back (TokenResponse.java has no such field) —
 * when the caller leaves it blank, AuthController.java generates a random one
 * server-side that only ever shows up as a claim on the access token itself
 * (JwtTokenService.java). Operator tokens carry no merchantId claim at all.
 */
interface AccessTokenClaims {
  merchantId?: string;
}

const STORAGE_KEY = 'payflow.auth';

function emptyAuthState(): AuthState {
  return { accessToken: null, refreshToken: null, role: null, merchantId: null, expiresAt: null };
}

function rehydrate(): AuthState {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return emptyAuthState();
    return { ...emptyAuthState(), ...JSON.parse(raw) };
  } catch {
    return emptyAuthState();
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly _state = signal<AuthState>(rehydrate());

  readonly role = computed(() => this._state().role);
  readonly merchantId = computed(() => this._state().merchantId);
  readonly isAuthenticated = computed(() => this._state().accessToken !== null);
  readonly accessToken = computed(() => this._state().accessToken);

  private pendingRefresh: Observable<TokenResponse> | null = null;

  constructor() {
    effect(() => {
      const state = this._state();
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    });
  }

  /** REAL: POST /auth/token */
  login(role: Role, merchantId?: string): Observable<TokenResponse> {
    const body: TokenRequest = { role, merchantId };
    return this.http
      .post<TokenResponse>(`${environment.apiBase}/auth/token`, body)
      .pipe(tap((res) => this.applyTokenResponse(res, role)));
  }

  /**
   * REAL: POST /auth/refresh. A 401 here means the refresh token itself is
   * invalid/expired/revoked (a documented, expected response per the Catalog) — not a
   * case to retry. Handle it once, in-place, by logging out; never recurse into
   * another refresh.
   */
  refreshAccessToken(): Observable<TokenResponse> {
    if (this.pendingRefresh) {
      return this.pendingRefresh;
    }
    const refreshToken = this._state().refreshToken;
    const body: RefreshTokenRequest = { refreshToken: refreshToken ?? '' };
    this.pendingRefresh = this.http.post<TokenResponse>(`${environment.apiBase}/auth/refresh`, body).pipe(
      tap((res) => this.applyTokenResponse(res, this._state().role)),
      catchError((error: unknown) => {
        this.revokeAndLogout().subscribe({ error: () => undefined });
        return throwError(() => error);
      }),
      shareReplay({ bufferSize: 1, refCount: true }),
      finalize(() => {
        this.pendingRefresh = null;
      }),
    );
    return this.pendingRefresh;
  }

  /** REAL: POST /auth/revoke */
  revokeAndLogout(): Observable<{ status: string }> {
    const refreshToken = this._state().refreshToken;
    const body: RefreshTokenRequest = { refreshToken: refreshToken ?? '' };
    return this.http.post<{ status: string }>(`${environment.apiBase}/auth/revoke`, body).pipe(
      finalize(() => {
        this._state.set(emptyAuthState());
        sessionStorage.removeItem(STORAGE_KEY);
        this.router.navigateByUrl('/signin');
      }),
    );
  }

  /** Re-issues a token for the given role (real re-login, not a client-side flip). */
  switchRole(role: Role, merchantId?: string): Observable<TokenResponse> {
    return this.login(role, merchantId);
  }

  private applyTokenResponse(res: TokenResponse, role: Role | null): void {
    const claims = decodeJwtPayload<AccessTokenClaims>(res.accessToken);
    this._state.set({
      accessToken: res.accessToken,
      refreshToken: res.refreshToken,
      role,
      merchantId: claims?.merchantId ?? null,
      expiresAt: Date.now() + res.expiresIn * 1000,
    });
  }
}
