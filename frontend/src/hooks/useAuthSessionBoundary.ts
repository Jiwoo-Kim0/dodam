import { usePathname, useRouter } from 'expo-router';
import { useEffect, useRef } from 'react';
import { Alert } from 'react-native';

import { routes } from '@/constants/routes';
import { subscribeAuthSessionExpired } from '@/services/authSessionService';

export function useAuthSessionBoundary() {
  const pathname = usePathname();
  const router = useRouter();
  const pathnameRef = useRef(pathname);

  useEffect(() => {
    pathnameRef.current = pathname;
  }, [pathname]);

  useEffect(
    () =>
      subscribeAuthSessionExpired(() => {
        if (pathnameRef.current === routes.login) return;

        router.replace(routes.login);
        Alert.alert('로그인이 만료됐어요', '다시 로그인해주세요.');
      }),
    [router],
  );
}
