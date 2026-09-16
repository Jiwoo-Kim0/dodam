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
    load,
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
        onRefresh={() => { void load(); }}
        refreshing={isLoading}
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

            <Text style={styles.summaryHeadline}>
              {isLoading
                ? '진단 결과를 확인하고 있어요'
                : `확인할 내용을 ${insights.length}개로 정리했어요`}
            </Text>

            <View style={styles.summaryPill}>
              <Text style={styles.summaryPillLabel}>
                추천별 예상 효과는 상세에서 확인할 수 있어요
              </Text>
            </View>
          </View>

          <View style={styles.listSection}>
            <Text style={styles.sectionTitle}>발견한 내용 {insights.length}개</Text>
            {error ? <Text style={styles.sectionTitle}>{error}</Text> : null}

            {insights.map((insight) => (
              <DiscoveryInsightsCard
                insight={insight}
                key={insight.id}
                onPress={() => router.push(routes.discoveryInsightDetail(insight.id))}
              />
            ))}
            {!isLoading && insights.length === 0 && !error ? (
              <Text style={styles.sectionTitle}>
                현재 진단에서 발견된 항목이 없어요. 분석되지 않은 항목은 제외됩니다.
              </Text>
            ) : null}
            {(error || !diagnosis || canRetryRecommendations) && !isLoading ? (
              <Pressable
                accessibilityRole="button"
                onPress={() => {
                  void (canRetryRecommendations
                    ? retryRecommendationGeneration()
                    : error
                      ? load()
                      : runAnalysis());
                }}
                style={styles.summaryPill}>
                <Text style={styles.summaryPillValue}>
                  {canRetryRecommendations
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

      {diagnosis && !isLoading ? (
        <View style={styles.fixedFooter}>
          {cooldown.label ? (
            <Text style={styles.cooldownLabel}>{cooldown.label}</Text>
          ) : null}
          <Pressable
            accessibilityRole="button"
            accessibilityState={{ disabled: !cooldown.canRun }}
            disabled={!cooldown.canRun}
            onPress={() => { void runAnalysis(true); }}
            style={({ pressed }) => [
              styles.diagnosisButton,
              !cooldown.canRun && styles.disabledButton,
              pressed && cooldown.canRun && styles.diagnosisButtonPressed,
            ]}>
            <Text style={styles.diagnosisButtonText}>새로 진단하기</Text>
          </Pressable>
        </View>
      ) : null}
    </SafeAreaLayout>
  );
}
