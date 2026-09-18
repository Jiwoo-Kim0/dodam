import { useRouter } from 'expo-router';
import { LinearGradient } from 'expo-linear-gradient';
import { useState } from 'react';
import { type LayoutChangeEvent, Pressable, Text, View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { colors } from '@/styles';

import { DiscoveryInsightsCard } from './components/DiscoveryInsightsCard';
import { styles } from './DiscoveryInsightsScreen.styles';
import { useDiscoveryData } from './hooks/useDiscoveryData';

export function DiscoveryInsightsScreen() {
  const router = useRouter();
  const [summaryCardHeight, setSummaryCardHeight] = useState(0);
  const [gradientContainerHeight, setGradientContainerHeight] = useState(0);
  const {
    canRetryRecommendations,
    cooldown,
    diagnosis,
    error,
    insights,
    isLoading,
    isRefreshing,
    isRunningAnalysis,
    load,
    refetch,
    retryRecommendationGeneration,
    runAnalysis,
  } = useDiscoveryData();

  const handleSummaryCardLayout = (event: LayoutChangeEvent) => {
    setSummaryCardHeight(event.nativeEvent.layout.height);
  };

  const handleGradientContainerLayout = (event: LayoutChangeEvent) => {
    setGradientContainerHeight(event.nativeEvent.layout.height);
  };

  return (
    <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
      <AppHeader showBackButton title="발견" />

      <RefreshableBouncyScrollView
        contentContainerStyle={styles.scrollContent}
        onRefresh={() => { void refetch(); }}
        refreshing={isRefreshing}
        style={styles.scroll}>
        <View onLayout={handleGradientContainerLayout} style={styles.gradientContainer}>
          {summaryCardHeight > 0 && gradientContainerHeight > summaryCardHeight ? (
            <LinearGradient
              colors={[colors.background, colors.home.goalGradientEnd]}
              style={[
                styles.gradientOverlay,
                {
                  height: gradientContainerHeight - summaryCardHeight,
                  top: summaryCardHeight,
                },
              ]}
            />
          ) : null}

          <View onLayout={handleSummaryCardLayout} style={styles.summaryCard}>
            <View style={styles.summaryHeaderRow}>
              <Text style={styles.summaryLabel}>지금 바꿀 수 있는 것</Text>
              <Text style={styles.summaryCount}>{insights.length}개</Text>
            </View>

            {/* 새로고침 중에 문구를 바꾸면 이미 나온 개수가 사라졌다 돌아오는 것처럼 보인다. */}
            <Text style={styles.summaryHeadline}>
              {isLoading && diagnosis === null
                ? '진단 결과를 확인하고 있어요'
                : `확인할 내용을 ${insights.length}개로 정리했어요`}
            </Text>

            <View style={styles.summaryNote}>
              <Text style={styles.summaryNoteLabel}>
                추천별 예상 효과는 상세에서 확인할 수 있어요
              </Text>
            </View>
          </View>

          <View style={styles.listSection}>
            {/* 개수는 바로 위 요약 카드가 이미 두 번 말한다. 여기서는 제목만 둔다. */}
            <Text style={styles.sectionTitle}>발견한 내용</Text>
            {error ? <Text style={styles.stateMessage}>{error}</Text> : null}

            {insights.map((insight) => (
              <DiscoveryInsightsCard
                insight={insight}
                key={insight.id}
                onPress={() => router.push(routes.discoveryInsightDetail(insight.id))}
              />
            ))}
            {!isLoading && insights.length === 0 && !error ? (
              <Text style={styles.stateMessage}>
                현재 진단에서 발견된 항목이 없어요. 분석되지 않은 항목은 제외됩니다.
              </Text>
            ) : null}
            {(isRunningAnalysis || error || !diagnosis || canRetryRecommendations) &&
            (!isLoading || isRunningAnalysis) ? (
              <Pressable
                accessibilityRole="button"
                accessibilityState={{
                  busy: isRunningAnalysis,
                  disabled: isRunningAnalysis,
                }}
                disabled={isRunningAnalysis}
                onPress={() => {
                  void (canRetryRecommendations
                    ? retryRecommendationGeneration()
                    : error
                      ? load()
                      : runAnalysis());
                }}
                style={[
                  styles.retryButton,
                  isRunningAnalysis && styles.disabledButton,
                ]}>
                <Text style={styles.retryButtonText}>
                  {isRunningAnalysis
                    ? '진단 중...'
                    : canRetryRecommendations
                    ? '추천 생성 다시 시도'
                    : error
                      ? '다시 시도하기'
                      : '진단 시작하기'}
                </Text>
              </Pressable>
            ) : null}
          </View>
        </View>
      </RefreshableBouncyScrollView>

      {diagnosis ? (
        <View style={styles.fixedFooter}>
          {cooldown.label ? (
            <Text style={styles.cooldownLabel}>{cooldown.label}</Text>
          ) : null}
          <Pressable
            accessibilityRole="button"
            accessibilityState={{
              busy: isRunningAnalysis,
              disabled: isLoading || !cooldown.canRun,
            }}
            disabled={isLoading || !cooldown.canRun}
            onPress={() => { void runAnalysis(true); }}
            style={({ pressed }) => [
              styles.diagnosisButton,
              (isLoading || !cooldown.canRun) && styles.disabledButton,
              pressed &&
                !isLoading &&
                cooldown.canRun &&
                styles.diagnosisButtonPressed,
            ]}>
            <Text style={styles.diagnosisButtonText}>
              {isRunningAnalysis ? '진단 중...' : '새로 진단하기'}
            </Text>
          </Pressable>
        </View>
      ) : null}
    </SafeAreaLayout>
  );
}
