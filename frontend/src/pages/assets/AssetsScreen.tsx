import { useRouter } from 'expo-router';
import { View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { ScreenState } from '@/components/screenState/ScreenState';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';

import { AssetOverviewCard } from './components/AssetOverviewCard';
import { AssetAnalysisCard } from './components/AssetAnalysisCard';
import { AssetSection } from './components/AssetSection';
import { useAssetData } from './hooks/useAssetData';
import { styles } from './AssetsScreen.styles';

export function AssetsScreen() {
  const router = useRouter();
  const {
    analysis,
    error,
    hasAssets,
    isLoading,
    isRefreshing,
    overview,
    refetch,
    sections,
  } = useAssetData();

  if (isLoading || error || !overview) {
    return (
      <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
        <AppHeader showBackButton title="내 자산" />

        <ScreenState
          description={
            error
              ? __DEV__
                ? error.message
                : '네트워크 상태를 확인한 뒤 다시 시도해주세요.'
              : '자산을 불러오고 있어요'
          }
          isLoading={!error}
          onRetry={error ? () => void refetch() : undefined}
          title={error ? '자산을 불러오지 못했어요.' : undefined}
        />
      </SafeAreaLayout>
    );
  }

  if (!hasAssets) {
    return (
      <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
        <AppHeader showBackButton title="내 자산" />
        <ScreenState
          actionLabel="금융 연결하기"
          description="금융기관을 연결하면 계좌·예적금·대출·카드 내역을 확인할 수 있어요."
          onAction={() => router.push(routes.financeConnection)}
          title="연결된 자산이 없어요."
        />
      </SafeAreaLayout>
    );
  }

  return (
    <SafeAreaLayout
      edges={['top', 'left', 'right']}
      style={styles.screen}
    >
      <AppHeader showBackButton title="내 자산" />

      <RefreshableBouncyScrollView
        contentContainerStyle={styles.scrollContent}
        onRefresh={() => void refetch()}
        refreshing={isRefreshing}>
        <AssetOverviewCard overview={overview} />

        {analysis ? <AssetAnalysisCard analysis={analysis} /> : null}

        <View style={styles.sections}>
          {sections.map((section) => (
            <AssetSection key={section.id} section={section} />
          ))}
        </View>
      </RefreshableBouncyScrollView>
    </SafeAreaLayout>
  );
}
