import { useRouter } from 'expo-router';
import { useEffect } from 'react';

import { authApi } from '@/api';
import { routes } from '@/constants/routes';
import { SplashScreen } from '@/pages/splash/SplashScreen';

const SPLASH_DURATION_MS = 3400;

export default function IndexRoute() {
  const router = useRouter();

  useEffect(() => {
    let isActive = true;
    let timer: ReturnType<typeof setTimeout>;

    const splashDelay = new Promise<void>((resolve) => {
      timer = setTimeout(resolve, SPLASH_DURATION_MS);
    });

    void Promise.all([splashDelay, authApi.restoreSession()])
      .then(([, result]) => {
        if (!isActive) return;

        router.replace(
          result.status === 'authenticated' ? routes.home : routes.login,
        );
      })
      .catch(() => {
        if (isActive) router.replace(routes.login);
      });

    return () => {
      isActive = false;
      clearTimeout(timer);
    };
  }, [router]);

  return <SplashScreen />;
}
