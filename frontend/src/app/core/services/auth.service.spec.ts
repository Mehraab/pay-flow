import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

const STORAGE_KEY = 'payflow.auth';

// Real token shapes issued by JwtTokenService.java (header/payload only; the signature
// is never verified client-side, so a fake one is fine for these tests).
const MERCHANT_TOKEN =
  'eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiUk9MRV9NRVJDSEFOVCIsIm1lcmNoYW50SWQiOiI1YzdhYzNlYS1kOGZmLTRkYWItOGIzZS0yMzI2ZWRiOWZhZGEifQ.fakesig';
const OPERATOR_TOKEN = 'eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiUk9MRV9PUEVSQVRPUiJ9.fakesig';

function tokenResponse(accessToken: string, refreshToken: string) {
  return { accessToken, refreshToken, tokenType: 'Bearer', expiresIn: 900 };
}

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    sessionStorage.removeItem(STORAGE_KEY);
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    sessionStorage.removeItem(STORAGE_KEY);
  });

  it('login() with a blank merchantId still populates merchantId from the JWT claim — TokenResponse never echoes the server-generated id back, only the access token carries it', () => {
    service.login('MERCHANT').subscribe();

    const req = httpMock.expectOne(`${environment.apiBase}/auth/token`);
    expect(req.request.body).toEqual({ role: 'MERCHANT', merchantId: undefined });
    req.flush(tokenResponse(MERCHANT_TOKEN, 'refresh-1'));

    expect(service.merchantId()).toBe('5c7ac3ea-d8ff-4dab-8b3e-2326edb9fada');
    expect(service.role()).toBe('MERCHANT');
    expect(service.isAuthenticated()).toBe(true);
  });

  it('login() as OPERATOR leaves merchantId null (JwtTokenService omits the claim entirely)', () => {
    service.login('OPERATOR').subscribe();

    httpMock.expectOne(`${environment.apiBase}/auth/token`).flush(tokenResponse(OPERATOR_TOKEN, 'refresh-2'));

    expect(service.merchantId()).toBeNull();
    expect(service.role()).toBe('OPERATOR');
  });

  it('refreshAccessToken() re-derives merchantId from the refreshed token too', () => {
    service.login('MERCHANT').subscribe();
    httpMock.expectOne(`${environment.apiBase}/auth/token`).flush(tokenResponse(MERCHANT_TOKEN, 'refresh-1'));

    service.refreshAccessToken().subscribe();
    httpMock
      .expectOne(`${environment.apiBase}/auth/refresh`)
      .flush(tokenResponse(MERCHANT_TOKEN, 'refresh-1-new'));

    expect(service.merchantId()).toBe('5c7ac3ea-d8ff-4dab-8b3e-2326edb9fada');
  });
});
