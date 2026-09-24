import { Image } from 'expo-image';
import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback } from 'react';
import { Text, View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { ScreenState } from '@/components/screenState/ScreenState';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';

import { EfficiencyScoreCard } from './components/EfficiencyScoreCard';
import { CoachingCard } from './components/CoachingCard';
import { FinancePatternCard } from './components/FinancePatternCard';
import { KeyMetricsCard } from './components/KeyMetricsCard';
import { MonthlySpendingCard } from './components/MonthlySpendingCard';
import { PeerComparisonCard } from './components/PeerComparisonCard';
import { SpendingForecastCard } from './components/SpendingForecastCard';
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
            {/*
             * 리포트는 끝난 달만 만들기 때문에(BE ReportPeriodPolicy) 9월에 열어도 기준 달은 8월이다.
             * 달 이름만 두면 앱이 지난달에 멈춘 것처럼 읽혀서 "기준"을 붙여 어느 달 분석인지 밝힌다.
             */}
            <Text style={styles.monthLabel}>{monthLabel ? `${monthLabel} 기준` : '최근 월 기준'}</Text>
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
            <View style={[styles.card, styles.introCard]}>
              <Image
                contentFit="contain"
                source={require('@/assets/characters/dori/dori_report.png')}
                style={styles.introCharacter}
              />
              <Text style={styles.introText}>
                {`${monthLabel} 한 달, ${data.userName}님의 금융 생활을 분석했어요.`}
              </Text>
            </View>

            <View style={styles.sections}>
              <EfficiencyScoreCard
                efficiencyScore={data.efficiencyScore}
                onPress={() => router.push(routes.reportEfficiencyScore)}
              />
              {data.peerComparison === null ? null : (
                <PeerComparisonCard
                  onPress={() => router.push(routes.reportPeerComparison)}
                  peerComparison={data.peerComparison}
                />
              )}
              <FinancePatternCard patterns={data.patterns} />
              <CoachingCard
                characterImage={data.patternCharacterImage}
                disclaimer={data.patternCoachingDisclaimer}
                message={data.patternCoachingMessage}
              />
              <KeyMetricsCard metrics={data.keyMetrics} />
              <MonthlySpendingCard pastSpending={data.pastSpending} />
              {data.spendingForecast ? (
                <SpendingForecastCard forecast={data.spendingForecast} />
              ) : null}
            </View>
          </>
        ) : (
          <ScreenState
            description={
              error
                ? '네트워크 상태를 확인한 뒤 금융 데이터를 다시 불러와 주세요.'
                : '지난달 거래가 쌓이면 월간 금융 리포트를 확인할 수 있어요.'
            }
            onRetry={error ? () => void refetch() : undefined}
            title={error ? '리포트를 불러오지 못했어요.' : '아직 리포트가 없어요.'}
          />
        )}
      </RefreshableBouncyScrollView>
    </SafeAreaLayout>
  );
}
