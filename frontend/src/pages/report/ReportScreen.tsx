import { useRouter } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { Text, View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { MONTHLY_REPORT } from '@/mocks/report';
import { colors } from '@/styles';

import { EfficiencyScoreCard } from './components/EfficiencyScoreCard';
import { FinancePatternCard } from './components/FinancePatternCard';
import { KeyMetricsCard } from './components/KeyMetricsCard';
import { MonthlySpendingCard } from './components/MonthlySpendingCard';
import { NextMonthForecastCard } from './components/NextMonthForecastCard';
import { styles } from './ReportScreen.styles';

export function ReportScreen() {
  const router = useRouter();
  const {
    efficiencyScore,
    keyMetrics,
    monthLabel,
    monthlySpending,
    nextMonthForecast,
    patternCharacterImage,
    patternCoachingMessage,
    patterns,
    userName,
  } = MONTHLY_REPORT;

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
          // 월 선택은 리포트 API 연결 이후 동작을 연결한다.
          <View style={styles.monthSelector}>
            <Text style={styles.monthLabel}>{monthLabel}</Text>
            <SymbolView
              name={{
                android: 'keyboard_arrow_down',
                ios: 'chevron.down',
                web: 'keyboard_arrow_down',
              }}
              size={18}
              tintColor={colors.textPrimary}
            />
          </View>
        }
        showBackButton
        title="리포트"
      />

      <RefreshableBouncyScrollView contentContainerStyle={styles.scrollContent}>
        <Text style={styles.screenDescription}>
          {`${monthLabel} 한 달, ${userName}님의 금융 생활을 분석했어요.`}
        </Text>

        <View style={styles.sections}>
          <EfficiencyScoreCard efficiencyScore={efficiencyScore} />
          <FinancePatternCard
            characterImage={patternCharacterImage}
            coachingMessage={patternCoachingMessage}
            patterns={patterns}
          />
          <NextMonthForecastCard forecast={nextMonthForecast} />
          <KeyMetricsCard metrics={keyMetrics} />
          <MonthlySpendingCard monthlySpending={monthlySpending} />
        </View>
      </RefreshableBouncyScrollView>
    </SafeAreaLayout>
  );
}
