import { useCallback } from 'react';
import { Platform } from 'react-native';

import { ApiError, isApiError, socialAuthApi } from '@/api';
import type { SocialLoginResult } from '@/types/auth.types';

/**
 * 구글 클라우드 콘솔의 **웹 애플리케이션 유형** 클라이언트 ID.
 * Android SDK가 id_token을 받을 때 `serverClientId`로 쓰이고, 서버는 이 값을 id_token의 `aud`로 검증한다.
 * 서버 `.env`의 `GOOGLE_CLIENT_ID`와 같은 값이어야 한다(infra/docs/external-integrations.md 4절).
 */
const GOOGLE_WEB_CLIENT_ID =
  process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID?.trim() ?? '';

interface NativeError {
  code?: string;
  message?: string;
}

function getNativeError(error: unknown): NativeError | null {
  if (!error || typeof error !== 'object') return null;

  const nativeError = error as NativeError;
  return {
    code: typeof nativeError.code === 'string' ? nativeError.code : undefined,
    message:
      typeof nativeError.message === 'string' ? nativeError.message : undefined,
  };
}

function isNativeModuleUnavailable(error: NativeError | null) {
  const message = error?.message ?? '';
  return (
    message.includes("doesn't seem to be linked") ||
    message.includes('RNGoogleSignin') ||
    message.includes('native module')
  );
}

export function useGoogleLogin() {
  const login = useCallback(async (): Promise<SocialLoginResult | null> => {
    if (!GOOGLE_WEB_CLIENT_ID) {
      throw new ApiError({
        status: 0,
        code: 'GOOGLE_LOGIN_NOT_CONFIGURED',
        message: '구글 웹 클라이언트 ID 설정이 필요합니다.',
      });
    }

    if (Platform.OS === 'web') {
      throw new ApiError({
        status: 0,
        code: 'GOOGLE_LOGIN_UNSUPPORTED_PLATFORM',
        message: '구글 로그인은 Android 또는 iOS 앱에서 이용해주세요.',
      });
    }

    try {
      // 카카오와 같은 이유로 늦게 불러온다. 네이티브 모듈이 없는 Expo Go에서도 나머지 화면은 열린다.
      const { GoogleSignin, isSuccessResponse } = await import(
        '@react-native-google-signin/google-signin'
      );

      GoogleSignin.configure({ webClientId: GOOGLE_WEB_CLIENT_ID });
      await GoogleSignin.hasPlayServices({ showPlayServicesUpdateDialog: true });

      const response = await GoogleSignin.signIn();
      if (!isSuccessResponse(response)) return null;

      // 서버는 accessToken이 아니라 id_token을 검증한다(backend/docs/auth-social.md).
      const idToken = response.data.idToken;
      if (!idToken) {
        throw new ApiError({
          status: 0,
          code: 'GOOGLE_ID_TOKEN_MISSING',
          message: '구글 계정 확인에 실패했습니다. 다시 시도해주세요.',
        });
      }

      return await socialAuthApi.loginWithGoogle(idToken);
    } catch (error) {
      if (isApiError(error)) throw error;

      const nativeError = getNativeError(error);
      if (nativeError?.code?.toLowerCase().includes('cancel')) return null;

      throw new ApiError({
        status: 0,
        code: isNativeModuleUnavailable(nativeError)
          ? 'GOOGLE_NATIVE_MODULE_NOT_AVAILABLE'
          : nativeError?.code ?? 'GOOGLE_LOGIN_FAILED',
        message: isNativeModuleUnavailable(nativeError)
          ? '구글 로그인은 개발 빌드에서 확인할 수 있습니다.'
          : '구글 로그인을 완료하지 못했습니다. 다시 시도해주세요.',
        cause: error,
      });
    }
  }, []);

  return {
    isConfigured: GOOGLE_WEB_CLIENT_ID.length > 0,
    login,
  };
}
