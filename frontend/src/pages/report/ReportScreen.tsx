import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback } from 'react';
import { Text, View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { ScreenState } from '@/components/screenState/ScreenState';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';

import { EfficiencyScoreCard } from './components/EfficiencyScoreCard';
import { FinancePatternCard } from './components/FinancePatternCard';
import { KeyMetricsCard } from './components/KeyMetricsCard';
import { MonthlySpendingCard } from './components/MonthlySpendingCard';
import { NextMonthForecastCard } from './components/NextMonthForecastCard';
import { styles } from './ReportScreen.styles';
import { useReportData } from './hooks/useReportData';

export function ReportScreen() {
  const router = useRouter();
  const { cancel, error, isLoading, isRefreshing, refetch, reload, report } = useReportData();
  useFocusEffect(useCallback(() => {
    void reload();
    return cancel;
  }, [cancel, reload]));
  const monthLabel = report?.monthLabel ?? '';
  const data = report;

  const handleBackPress = () => {
    if (router.canGoBack()) {
      router.back();
      return;
    }

    router.replace(routes.home);
  };

  return (
    <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
      <AppHeader
        onBackPress={handleBackPress}
        right={
          <View style={styles.monthSelector}>
            <Text style={styles.monthLabel}>{monthLabel || '최근 월'}</Text>
          </View>
        }
        showBackButton
        title="리포트"
      />

      <RefreshableBouncyScrollView
        contentContainerStyle={styles.scrollContent}
        onRefresh={() => void refetch()}
        refreshing={isRefreshing}>
        {isLoading ? (
          <ScreenState description="리포트를 불러오고 있어요" isLoading />
        ) : data ? (
          <>
            <Text style={styles.screenDescription}>
              {`${monthLabel} 한 달, ${data.userName}님의 금융 생활을 분석했어요.`}
            </Text>

            <View style={styles.sections}>
              <EfficiencyScoreCard efficiencyScore={data.efficiencyScore} />
              <FinancePatternCard
                characterImage={data.patternCharacterImage}
                coachingMessage={data.patternCoachingMessage}
                disclaimer={data.patternCoachingDisclaimer}
                patterns={data.patterns}
              />
              <NextMonthForecastCard forecast={data.nextMonthForecast} />
              <KeyMetricsCard metrics={data.keyMetrics} />
              <MonthlySpendingCard monthlySpending={data.monthlySpending} />
            </View>
          </>
        ) : (
          <ScreenState
            description={
              error
                ? '금융 데이터를 다시 불러와 주세요.'
                : '완료된 월의 금융 데이터가 없어요.'
            }
            onRetry={error ? () => void refetch() : undefined}
            title="리포트를 표시할 수 없어요"
          />
        )}
      </RefreshableBouncyScrollView>
    </SafeAreaLayout>
  );
}
