import { clearAuthTokens, getAuthTokens, setAuthTokens } from '@/services/authTokenStorage';
import type {
  AuthSession,
  AuthSessionRestoreResult,
  AuthResponse,
  AuthTokens,
  LoginRequest,
  SessionDeleteResponse,
} from '@/types/auth.types';

import { apiClient, refreshAuthTokens } from './apiClient';
import { isApiError } from './ApiError';

const AUTH_PATH = '/v1/auth';

function getTokensFromResponse(response: AuthResponse): AuthTokens {
  return {
    tokenType: response.tokenType,
    accessToken: response.accessToken,
    accessTokenExpiresAt: response.accessTokenExpiresAt,
    refreshToken: response.refreshToken,
    refreshTokenExpiresAt: response.refreshTokenExpiresAt,
  };
}

export async function persistAuthResponse(response: AuthResponse) {
  await setAuthTokens(getTokensFromResponse(response));
}

/** 이메일·비밀번호 로그인. 응답 본문은 소셜 로그인과 같다. */
export async function login(request: LoginRequest): Promise<AuthResponse> {
  const response = await apiClient.post<AuthResponse>(`${AUTH_PATH}/login`, {
    auth: false,
    body: request,
  });

  await persistAuthResponse(response);
  return response;
}

export async function logout() {
  try {
    const tokens = await getAuthTokens();
    if (tokens) {
      await apiClient.post<null>(`${AUTH_PATH}/logout`);
    }
  } finally {
    await clearAuthTokens();
  }
}

export function getSession() {
  return apiClient.get<AuthSession>(`${AUTH_PATH}/session`);
}

export async function restoreAuthSession(): Promise<AuthSessionRestoreResult> {
  const tokens = await getAuthTokens();
  if (!tokens) return { status: 'unauthenticated', session: null };

  try {
    const session = await getSession();
    return { status: 'authenticated', session };
  } catch (error) {
    if (isApiError(error) && error.isUnauthorized) {
      await clearAuthTokens();
      return { status: 'unauthenticated', session: null };
    }

    throw error;
  }
}

export async function deleteSessions(sessionId?: number) {
  const currentSessionId = sessionId === undefined
    ? undefined
    : (await getSession()).sessionId;
  const response = await apiClient.delete<SessionDeleteResponse>(
    `${AUTH_PATH}/session`,
    { query: { sessionId } },
  );

  if (sessionId === undefined || sessionId === currentSessionId) {
    await clearAuthTokens();
  }

  return response;
}

export const authApi = {
  login,
  logout,
  refreshTokens: refreshAuthTokens,
  getSession,
  restoreSession: restoreAuthSession,
  deleteSessions,
};
