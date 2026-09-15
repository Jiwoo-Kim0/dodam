import { ActivityIndicator, Pressable, Text, View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { colors } from '@/styles';

import { AssetOverviewCard } from './components/AssetOverviewCard';
import { AssetSection } from './components/AssetSection';
import { useAssetData } from './hooks/useAssetData';
import { styles } from './AssetsScreen.styles';

export function AssetsScreen() {
  const {
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

        <View style={styles.statusContainer}>
          {error ? (
            <>
              <Text style={styles.statusTitle}>자산을 불러오지 못했어요.</Text>
              <Text style={styles.statusDescription}>
                {__DEV__
                  ? error.message
                  : '네트워크 상태를 확인한 뒤 다시 시도해주세요.'}
              </Text>
              <Pressable
                accessibilityRole="button"
                onPress={() => void refetch()}
                style={({ pressed }) => [
                  styles.retryButton,
                  pressed && styles.retryButtonPressed,
                ]}
              >
                <Text style={styles.retryButtonLabel}>다시 시도</Text>
              </Pressable>
            </>
          ) : (
            <ActivityIndicator color={colors.primary.green} size="small" />
          )}
        </View>
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

        <View style={styles.sections}>
          {sections.map((section) => (
            <AssetSection key={section.id} section={section} />
          ))}
        </View>
      </RefreshableBouncyScrollView>
    </SafeAreaLayout>
  );
}
