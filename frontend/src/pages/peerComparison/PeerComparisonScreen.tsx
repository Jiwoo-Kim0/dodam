import { Image } from 'expo-image';
import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback } from 'react';
import { Text, View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { ScreenState } from '@/components/screenState/ScreenState';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { formatNumber } from '@/utils/currency';

import { PeerDistributionBars } from './components/PeerDistributionBars';
import { usePeerComparison } from './hooks/usePeerComparison';
import { styles } from './PeerComparisonScreen.styles';

export function PeerComparisonScreen() {
  const router = useRouter();
  const { cancel, error, isLoading, isRefreshing, peerComparison, refetch, reload } =
    usePeerComparison();
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
      <AppHeader onBackPress={handleBackPress} showBackButton title="나의 소비 유형" />

      <RefreshableBouncyScrollView
        contentContainerStyle={styles.scrollContent}
        onRefresh={() => void refetch()}
        refreshing={isRefreshing}>
        {isLoading ? (
          <ScreenState description="또래 비교를 불러오고 있어요" isLoading />
        ) : peerComparison === null ? (
          <ScreenState
            description="네트워크 상태를 확인한 뒤 다시 시도해 주세요."
            onRetry={error ? () => void reload() : undefined}
            title="또래 비교를 불러오지 못했어요."
          />
        ) : peerComparison.status === 'unavailable' ? (
          <ScreenState description={peerComparison.message} title="아직 비교할 수 없어요." />
        ) : (
          <>
            <View style={[styles.card, styles.heroCard]}>
              <View style={styles.heroContent}>
                <View style={styles.badge}>
                  <Text style={styles.badgeText}>K-Means 분석</Text>
                </View>

                {peerComparison.clusterLabel === null ? null : (
                  <Text style={styles.clusterLabel}>{peerComparison.clusterLabel}</Text>
                )}

                <Text style={styles.groupLabel}>
                  {`${peerComparison.groupLabel} `}
                  <Text style={styles.groupSampleSize}>
                    {`${formatNumber(peerComparison.sampleSize)}명`}
                  </Text>
                  과 비교했어요
                </Text>

                <View style={styles.heroNotes}>
                  {peerComparison.isFallback ? (
                    <Text style={styles.note}>
                      같은 조건의 또래가 적어 더 넓은 집단과 비교했어요
                    </Text>
                  ) : null}

                  {peerComparison.analysisPeriodLabel === null ? null : (
                    <Text style={styles.note}>{peerComparison.analysisPeriodLabel}</Text>
                  )}
                </View>
              </View>

              <Image
                contentFit="contain"
                source={require('@/assets/characters/tori/tori_interested.png')}
                style={styles.heroCharacter}
              />
            </View>

            <View style={styles.card}>
              <Text style={styles.cardTitle}>항목별 또래 비교</Text>
              <Text style={styles.cardDescription}>초록 점을 누르면 내 수치를 볼 수 있어요</Text>
              {/*
                여기는 분석 기간 전체를 평균한 실제 비율이다. 금융 효율 점수 화면은 한 달치
                점수라 숫자가 서로 달라, 같은 값으로 읽고 어긋난다고 느끼지 않도록 밝혀 둔다(698).
              */}
              <Text style={styles.cardDescription}>
                기간 평균 비율이라 금융 효율 점수 화면의 한 달 값과는 달라요
              </Text>

              <View style={styles.bars}>
                <PeerDistributionBars features={peerComparison.features} />
              </View>

              <View style={styles.legend}>
                <View style={styles.legendItem}>
                  <View style={styles.legendBandMark} />
                  <Text style={styles.legendText}>또래 절반</Text>
                </View>
                <View style={styles.legendItem}>
                  <View style={styles.legendMedianMark} />
                  <Text style={styles.legendText}>또래 중앙값</Text>
                </View>
                <View style={styles.legendItem}>
                  <View style={styles.legendDotMark} />
                  <Text style={styles.legendText}>나</Text>
                </View>
              </View>
            </View>

            {peerComparison.insights.length === 0 ? null : (
              <View style={styles.card}>
                <Text style={styles.cardTitle}>분석 요약</Text>

                <View style={styles.insightList}>
                  {peerComparison.insights.map((insight) => (
                    <View key={insight} style={styles.insightRow}>
                      <Text style={styles.insightText}>{insight}</Text>
                    </View>
                  ))}
                </View>
              </View>
            )}
          </>
        )}
      </RefreshableBouncyScrollView>
    </SafeAreaLayout>
  );
}
