import { useRouter } from 'expo-router';

import { routes } from '@/constants/routes';
import { MainLayout } from '@/layout/mainLayout/MainLayout';

export function ReportScreen() {
  const router = useRouter();

  return (
    <MainLayout
      onBackPress={() => router.replace(routes.home)}
      showBackButton
      title="리포트"
    />
  );
}
