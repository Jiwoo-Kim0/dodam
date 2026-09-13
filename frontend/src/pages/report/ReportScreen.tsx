import { useRouter } from 'expo-router';

import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { routes } from '@/constants/routes';
import { MainLayout } from '@/layout/mainLayout/MainLayout';

export function ReportScreen() {
  const router = useRouter();

  return (
    <MainLayout
      onBackPress={() => router.replace(routes.home)}
      padded={false}
      showBackButton
      title="리포트"
    >
      <RefreshableBouncyScrollView />
    </MainLayout>
  );
}
