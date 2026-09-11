import { useRouter } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { Pressable, Text, View } from 'react-native';

import { BouncyScrollView } from '@/components/bouncyScrollView/BouncyScrollView';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import {
  DAILY_BRIEFING,
  DAILY_BRIEFING_GOAL_IMPACTS,
  DAILY_BRIEFING_INSIGHTS,
  DAILY_BRIEFING_TRANSACTIONS,
} from '@/mocks/dailyBriefing';
import { colors } from '@/styles';

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
      <View style={styles.header}>
        <Pressable
          accessibilityLabel="홈으로 돌아가기"
          accessibilityRole="button"
          hitSlop={8}
          onPress={handleBackPress}
          style={({ pressed }) => [styles.backButton, pressed && styles.pressed]}>
          <SymbolView
            name={{ android: 'arrow_back', ios: 'chevron.left', web: 'arrow_back' }}
            size={24}
            tintColor={colors.textPrimary}
          />
        </Pressable>
      </View>

      <BouncyScrollView contentContainerStyle={styles.scrollContent}>
        <Text style={styles.screenTitle}>오늘의 도담 브리핑</Text>

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
      </BouncyScrollView>
    </SafeAreaLayout>
  );
}
