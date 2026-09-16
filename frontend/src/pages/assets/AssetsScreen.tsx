import { View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { ScreenState } from '@/components/screenState/ScreenState';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';

import { AssetOverviewCard } from './components/AssetOverviewCard';
import { AssetAnalysisCard } from './components/AssetAnalysisCard';
import { AssetSection } from './components/AssetSection';
import { useAssetData } from './hooks/useAssetData';
import { styles } from './AssetsScreen.styles';

export function AssetsScreen() {
  const {
    analysis,
    error,
    isLoading,
    isRefreshing,
    overview,
    refetch,
    sections,
  } = useAssetData();

  if (isLoading || !overview) {
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

  return (
    <SafeAreaLayout
      edges={['top', 'left', 'right']}
      style={styles.screen}
    >
      <AppHeader showBackButton title="내 자산" />

      <RefreshableBouncyScrollView
        contentContainerStyle={styles.scrollContent}
        onRefresh={() => void refetch()}
        refreshing={isRefreshing}
      >
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
