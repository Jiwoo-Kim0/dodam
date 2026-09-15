import type {
  KakaoLoginResponse,
  KakaoTokenExchangeRequest,
} from '@/types/auth.types';

import { apiClient } from './apiClient';
import { persistAuthResponse } from './authApi';

const KAKAO_LOGIN_PATH =
  process.env.EXPO_PUBLIC_KAKAO_LOGIN_PATH?.trim() || '/v1/auth/oauth/kakao';

/**
 * Temporary backend boundary for the native-SDK token verification flow.
 * If the backend chooses an Authorization header or an ID token instead, only
 * this adapter and its request type need to change.
 */
export async function exchangeKakaoToken(request: KakaoTokenExchangeRequest) {
  const response = await apiClient.post<KakaoLoginResponse>(KAKAO_LOGIN_PATH, {
    auth: false,
    body: request,
  });

  await persistAuthResponse(response);
  return response;
}

export const socialAuthApi = {
  exchangeKakaoToken,
};
