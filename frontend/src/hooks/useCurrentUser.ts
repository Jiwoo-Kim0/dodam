import { useCallback, useEffect, useState } from 'react';

import { userApi } from '@/api';
import type { UserProfile } from '@/types/user.types';

/**
 * 로그인한 사용자 정보(GET /v1/users/me).
 * 이름과 금융망 연결 상태를 함께 쓰는 화면들이 공유한다.
 */
export function useCurrentUser() {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    let isActive = true;

    userApi
      .getMe()
      .then((profile) => {
        if (!isActive) return;

        setUser(profile);
        setHasError(false);
      })
      .catch(() => {
        // 사용자 정보는 화면의 보조 정보라 실패해도 화면 자체는 유지한다.
        if (isActive) setHasError(true);
      })
      .finally(() => {
        if (isActive) setIsLoading(false);
      });

    return () => {
      isActive = false;
    };
  }, [reloadToken]);

  /** 연결 해제처럼 화면에서 바꾼 값을 다시 읽어올 때 쓴다. */
  const reload = useCallback(() => setReloadToken((token) => token + 1), []);

  return { user, isLoading, hasError, reload };
}
