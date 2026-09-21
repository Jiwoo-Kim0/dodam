import type {
  AuthResponse,
  SocialLoginRequest,
  SocialLoginResult,
  SocialProvider,
} from '@/types/auth.types';

import { apiClient } from './apiClient';
import { persistAuthResponse } from './authApi';

const SOCIAL_LOGIN_PATH = '/v1/auth/social';

/**
 * 공급자 SDK로 받은 토큰을 서버가 공급자에 검증하고 도담 세션으로 바꿔준다(backend/docs/auth-social.md).
 * 서버는 기존 회원이면 200, 신규 회원을 만들었으면 201을 준다.
 */
export async function loginWithSocialToken(
  provider: SocialProvider,
  token: string,
): Promise<SocialLoginResult> {
  const body: SocialLoginRequest = { token };
  const { data, status } = await apiClient.postWithStatus<AuthResponse>(
    `${SOCIAL_LOGIN_PATH}/${provider}`,
    { auth: false, body },
  );

  await persistAuthResponse(data);
  return { auth: data, isNewUser: status === 201 };
}

/** 구글은 accessToken이 아니라 id_token(JWT)을 보낸다. */
export function loginWithGoogle(idToken: string) {
  return loginWithSocialToken('google', idToken);
}

export const socialAuthApi = {
  loginWithSocialToken,
  loginWithGoogle,
};
