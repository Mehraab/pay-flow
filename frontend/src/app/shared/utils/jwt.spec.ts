import { decodeJwtPayload } from './jwt';

// Real token shapes issued by JwtTokenService.java (header/payload only, fake signature —
// decodeJwtPayload never verifies the signature, it only reads claims).
const MERCHANT_TOKEN =
  'eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiUk9MRV9NRVJDSEFOVCIsIm1lcmNoYW50SWQiOiI1YzdhYzNlYS1kOGZmLTRkYWItOGIzZS0yMzI2ZWRiOWZhZGEifQ.fakesig';
const OPERATOR_TOKEN = 'eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiUk9MRV9PUEVSQVRPUiJ9.fakesig';

interface AccessTokenClaims {
  role?: string;
  merchantId?: string;
}

describe('decodeJwtPayload', () => {
  it('decodes the merchantId claim from a real merchant access token', () => {
    const claims = decodeJwtPayload<AccessTokenClaims>(MERCHANT_TOKEN);
    expect(claims?.role).toBe('ROLE_MERCHANT');
    expect(claims?.merchantId).toBe('5c7ac3ea-d8ff-4dab-8b3e-2326edb9fada');
  });

  it('returns no merchantId claim for an operator token (JwtTokenService omits it entirely)', () => {
    const claims = decodeJwtPayload<AccessTokenClaims>(OPERATOR_TOKEN);
    expect(claims?.role).toBe('ROLE_OPERATOR');
    expect(claims?.merchantId).toBeUndefined();
  });

  it('returns null for a malformed token instead of throwing', () => {
    expect(decodeJwtPayload('not-a-jwt')).toBeNull();
    expect(decodeJwtPayload('')).toBeNull();
    expect(decodeJwtPayload('a.b')).toBeNull();
  });

  it('returns null for an unparseable payload segment instead of throwing', () => {
    expect(decodeJwtPayload('a.!!!notbase64!!!.c')).toBeNull();
  });
});
