import { useRouter } from 'expo-router';
import { useEffect } from 'react';

import { routes } from '@/constants/routes';
import { SplashScreen } from '@/pages/splash/SplashScreen';

const SPLASH_DURATION_MS = 3400;

export default function IndexRoute() {
  const router = useRouter();

  useEffect(() => {
    const timer = setTimeout(() => {
      router.replace(routes.login);
    }, SPLASH_DURATION_MS);

    return () => clearTimeout(timer);
  }, [router]);

  return <SplashScreen />;
}
