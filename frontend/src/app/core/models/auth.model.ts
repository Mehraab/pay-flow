export type Role = 'MERCHANT' | 'OPERATOR';

export interface TokenRequest {
  role: Role;
  merchantId?: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}
