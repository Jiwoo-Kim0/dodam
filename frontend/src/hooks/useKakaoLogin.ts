import { useCallback } from 'react';
import { Platform } from 'react-native';

import { ApiError, isApiError, socialAuthApi } from '@/api';
import type { SocialLoginResult } from '@/types/auth.types';

const KAKAO_NATIVE_APP_KEY =
  process.env.EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY?.trim() ?? '';

interface KakaoNativeError {
  code?: string;
  message?: string;
}

let kakaoSdkInitialization: Promise<void> | null = null;

async function initializeKakaoSdk() {
  if (!kakaoSdkInitialization) {
    kakaoSdkInitialization = import('@react-native-kakao/core')
      .then(({ initializeKakaoSDK }) =>
        initializeKakaoSDK(KAKAO_NATIVE_APP_KEY),
      )
      .catch((error: unknown) => {
        kakaoSdkInitialization = null;
        throw error;
      });
  }

  return kakaoSdkInitialization;
}

function getKakaoNativeError(error: unknown): KakaoNativeError | null {
  if (!error || typeof error !== 'object') return null;

  const nativeError = error as KakaoNativeError;
  return {
    code: typeof nativeError.code === 'string' ? nativeError.code : undefined,
    message:
      typeof nativeError.message === 'string' ? nativeError.message : undefined,
  };
}

function isLoginCancelled(error: KakaoNativeError | null) {
  return error?.code?.toLowerCase().includes('cancel') ?? false;
}

export function useKakaoLogin() {
  const login = useCallback(async (): Promise<SocialLoginResult | null> => {
    if (!KAKAO_NATIVE_APP_KEY) {
      throw new ApiError({
        status: 0,
        code: 'KAKAO_LOGIN_NOT_CONFIGURED',
        message: '카카오 네이티브 앱 키 설정이 필요합니다.',
      });
    }

    if (Platform.OS === 'web') {
      throw new ApiError({
        status: 0,
        code: 'KAKAO_LOGIN_UNSUPPORTED_PLATFORM',
        message: '카카오 로그인은 Android 또는 iOS 앱에서 이용해주세요.',
      });
    }

    try {
      // A development/production build contains this native module. Delaying
      // the import keeps Expo Go usable for UI work when the module is absent.
      await initializeKakaoSdk();
      const { login: loginWithKakaoSdk } = await import(
        '@react-native-kakao/user'
      );
      const kakaoToken = await loginWithKakaoSdk();

      return await socialAuthApi.loginWithKakao(kakaoToken.accessToken);
    } catch (error) {
      if (isApiError(error)) throw error;

      const nativeError = getKakaoNativeError(error);
      if (isLoginCancelled(nativeError)) return null;

      const isNativeModuleUnavailable = nativeError?.message?.includes(
        "doesn't seem to be linked",
      );

      throw new ApiError({
        status: 0,
        code: isNativeModuleUnavailable
          ? 'KAKAO_NATIVE_MODULE_NOT_AVAILABLE'
          : nativeError?.code ?? 'KAKAO_LOGIN_FAILED',
        message: isNativeModuleUnavailable
          ? '카카오 로그인은 개발 빌드에서 확인할 수 있습니다.'
          : '카카오 로그인을 완료하지 못했습니다. 다시 시도해주세요.',
        cause: error,
      });
    }
  }, []);

  return {
    isConfigured: KAKAO_NATIVE_APP_KEY.length > 0,
    login,
  };
}
