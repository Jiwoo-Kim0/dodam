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

/** POST /v1/auth/login 요청 본문. */
export interface LoginRequest {
  email: string;
  password: string;
}

/** 백엔드가 지원하는 소셜 로그인 공급자. 네이버는 아직 미지원이다. */
export type SocialProvider = 'kakao' | 'google';

/**
 * POST /v1/auth/social/{provider} 요청 본문.
 * 카카오는 SDK access token, 구글은 ID token(JWT)을 담는다.
 */
export interface SocialLoginRequest {
  token: string;
}

export interface SocialLoginResult {
  auth: AuthResponse;
  /** 201로 신규 회원이 생성된 경우. 온보딩(약관·금융 연동)으로 보내야 한다. */
  isNewUser: boolean;
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
