import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback } from 'react';
import { View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { ScreenState } from '@/components/screenState/ScreenState';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';

import { GoalImpactCard } from './components/GoalImpactCard';
import { InsightSection } from './components/InsightSection';
import { StreakSection } from './components/StreakSection';
import { SummaryCard } from './components/SummaryCard';
import { TransactionsCard } from './components/TransactionsCard';
import { UpcomingCard } from './components/UpcomingCard';
import { styles } from './DailyBriefingScreen.styles';
import { useDailyBriefing } from './hooks/useDailyBriefing';

export function DailyBriefingScreen() {
  const router = useRouter();
  const { cancel, data, error, isLoading, isRefreshing, refetch, reload } = useDailyBriefing();

  useFocusEffect(useCallback(() => {
    void reload();
    return cancel;
  }, [cancel, reload]));

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
        showBackButton
        title="오늘의 도담 브리핑"
      />

      <RefreshableBouncyScrollView
        contentContainerStyle={styles.scrollContent}
        onRefresh={() => void refetch()}
        refreshing={isRefreshing}>
        {isLoading ? <ScreenState description="어제의 소비를 살펴보고 있어요" isLoading /> : data ? (
          <View style={styles.sections}>
            <SummaryCard
              comparisonMessage={data.comparisonMessage}
              previousMonthDailyAverage={data.previousMonthDailyAverage}
              yesterdaySpentAmount={data.yesterdaySpentAmount}
            />
            <TransactionsCard message={data.transactionsMessage} transactions={data.transactions} />
            <InsightSection insights={data.insights} message={data.insightsMessage} />
            <GoalImpactCard impacts={data.goalImpacts} message={data.goalImpactsMessage} />
            <UpcomingCard
              description={data.upcomingEvent.description}
              title={data.upcomingEvent.title}
            />
            <StreakSection message={data.streaksMessage} streaks={data.streaks} />
          </View>
        ) : (
          <ScreenState
            description={error ? '금융 데이터를 다시 불러와 주세요.' : '어제의 금융 데이터가 없어요.'}
            onRetry={() => void refetch()}
            title="브리핑을 표시할 수 없어요"
          />
        )}
      </RefreshableBouncyScrollView>
    </SafeAreaLayout>
  );
}
