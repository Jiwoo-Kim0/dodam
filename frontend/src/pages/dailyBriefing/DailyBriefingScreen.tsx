import { useRouter } from 'expo-router';
import { View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import {
  DAILY_BRIEFING,
  DAILY_BRIEFING_GOAL_IMPACTS,
  DAILY_BRIEFING_INSIGHTS,
  DAILY_BRIEFING_TRANSACTIONS,
} from '@/mocks/dailyBriefing';

import { GoalImpactCard } from './components/GoalImpactCard';
import { InsightSection } from './components/InsightSection';
import { StreakSection } from './components/StreakSection';
import { SummaryCard } from './components/SummaryCard';
import { TransactionsCard } from './components/TransactionsCard';
import { UpcomingCard } from './components/UpcomingCard';
import { styles } from './DailyBriefingScreen.styles';

export function DailyBriefingScreen() {
  const router = useRouter();

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

      <RefreshableBouncyScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.sections}>
          <SummaryCard
            previousMonthDailyAverage={DAILY_BRIEFING.previousMonthDailyAverage}
            yesterdaySpentAmount={DAILY_BRIEFING.yesterdaySpentAmount}
          />
          <TransactionsCard transactions={DAILY_BRIEFING_TRANSACTIONS} />
          <InsightSection insights={DAILY_BRIEFING_INSIGHTS} />
          <GoalImpactCard impacts={DAILY_BRIEFING_GOAL_IMPACTS} />
          <UpcomingCard
            description={DAILY_BRIEFING.upcomingEvent.description}
            title={DAILY_BRIEFING.upcomingEvent.title}
          />
          <StreakSection streaks={DAILY_BRIEFING.streaks} />
        </View>
      </RefreshableBouncyScrollView>
    </SafeAreaLayout>
  );
}
