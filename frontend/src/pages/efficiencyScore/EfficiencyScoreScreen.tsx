import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback } from 'react';
import { Text, View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { ScreenState } from '@/components/screenState/ScreenState';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';

import { EfficiencyDimensionList } from './components/EfficiencyDimensionList';
import { useEfficiencyScore } from './hooks/useEfficiencyScore';
import { styles } from './EfficiencyScoreScreen.styles';

function differenceLabel(difference: number) {
  if (difference === 0) return '지난달과 같아요';
  return `지난달보다 ${Math.abs(difference)}점 ${difference > 0 ? '올랐어요' : '내렸어요'}`;
}

export function EfficiencyScoreScreen() {
  const router = useRouter();
  const { cancel, efficiencyScore, error, isLoading, isRefreshing, refetch, reload } =
    useEfficiencyScore();
  useFocusEffect(useCallback(() => {
    void reload();
    return cancel;
  }, [cancel, reload]));

  const handleBackPress = () => {
    if (router.canGoBack()) {
      router.back();
      return;
    }

    router.replace(routes.report);
  };

  return (
    <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
      <AppHeader onBackPress={handleBackPress} showBackButton title="금융 효율 점수" />

      <RefreshableBouncyScrollView
        contentContainerStyle={styles.scrollContent}
        onRefresh={() => void refetch()}
        refreshing={isRefreshing}>
        {isLoading ? (
          <ScreenState description="금융 효율 점수를 불러오고 있어요" isLoading />
        ) : efficiencyScore === null ? (
          <ScreenState
            description="네트워크 상태를 확인한 뒤 다시 시도해 주세요."
            onRetry={error ? () => void reload() : undefined}
            title="금융 효율 점수를 불러오지 못했어요."
          />
        ) : efficiencyScore.score === null ? (
          <ScreenState
            description={efficiencyScore.unavailableMessage ?? ''}
            title="아직 점수를 낼 수 없어요."
          />
        ) : (
          <>
            <View style={[styles.card, styles.heroCard]}>
              {/* 어느 달 점수인지 먼저 밝힌다. 또래 비교는 기간이 달라 값이 어긋나 보인다(698). */}
              {efficiencyScore.periodLabel === null ? null : (
                <Text style={styles.periodLabel}>{`${efficiencyScore.periodLabel} 기준`}</Text>
              )}

              <View style={styles.scoreRow}>
                <Text style={styles.score}>{efficiencyScore.score}</Text>
                <Text style={styles.scoreDivider}>/</Text>
                <Text style={styles.maxScore}>{efficiencyScore.maxScore}</Text>
              </View>

              {efficiencyScore.previousMonthDifference === null ? null : (
                <Text style={styles.difference}>
                  {differenceLabel(efficiencyScore.previousMonthDifference)}
                </Text>
              )}

              <Text style={styles.summary}>{efficiencyScore.summary}</Text>
            </View>

            {efficiencyScore.dimensions.length === 0 ? null : (
              <View style={styles.card}>
                <Text style={styles.cardTitle}>항목별 점수</Text>
                {/*
                  오른쪽 숫자가 실제 비율이 아니라 0~100점의 관리 점수다. 아래 설명에 적힌
                  비율과 다른 값이라, 사용자가 둘을 같은 값으로 읽지 않게 먼저 밝힌다(698).
                */}
                <Text style={styles.cardDescription}>
                  0~100점으로 환산한 관리 점수예요. 아래 설명의 실제 비율과는 다른 값이에요
                </Text>
                <Text style={styles.cardDescription}>
                  계산할 수 없는 항목은 전체 점수에 넣지 않아요
                </Text>

                <View style={styles.dimensions}>
                  <EfficiencyDimensionList dimensions={efficiencyScore.dimensions} />
                </View>
              </View>
            )}
          </>
        )}
      </RefreshableBouncyScrollView>
    </SafeAreaLayout>
  );
}
