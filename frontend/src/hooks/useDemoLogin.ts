import { useCallback } from 'react';

import { authApi } from '@/api';

const DEMO_EMAIL = process.env.EXPO_PUBLIC_DEMO_EMAIL?.trim() ?? '';
const DEMO_PASSWORD = process.env.EXPO_PUBLIC_DEMO_PASSWORD ?? '';

/**
 * 시연 계정(이메일·비밀번호) 로그인.
 * 계정 값은 `.env`에만 두고 저장소에는 넣지 않는다. 환경 변수가 없으면 `isAvailable`이 false라 진입점도 나오지 않는다.
 */
export function useDemoLogin() {
  const login = useCallback(
    () =>
      authApi.loginWithPassword({
        email: DEMO_EMAIL,
        password: DEMO_PASSWORD,
      }),
    [],
  );

  return {
    isAvailable: DEMO_EMAIL.length > 0 && DEMO_PASSWORD.length > 0,
    login,
  };
}
