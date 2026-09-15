export type UserRole = 'USER' | 'ADMIN';

export interface AuthUser {
  userId: number;
  email: string;
  name: string;
  role: UserRole;
}

export interface AuthTokens {
  tokenType: 'Bearer';
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
}

export interface AuthResponse extends AuthTokens {
  user: AuthUser;
}

export interface KakaoTokenExchangeRequest {
  accessToken: string;
}

export interface KakaoLoginResponse extends AuthResponse {
  /** Temporary contract: a missing value safely falls back to onboarding. */
  onboardingRequired?: boolean;
}

export interface AuthSession {
  user: AuthUser;
  sessionId: number;
  sessionCreatedAt: string;
  lastAccessedAt: string;
  sessionExpiresAt: string;
}

export interface SessionDeleteResponse {
  revokedCount: number;
}

export type AuthSessionRestoreResult =
  | { status: 'authenticated'; session: AuthSession }
  | { status: 'unauthenticated'; session: null };
