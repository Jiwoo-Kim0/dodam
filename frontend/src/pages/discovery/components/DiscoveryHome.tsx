import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useState } from 'react';
import { type LayoutChangeEvent, Pressable, Text, View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { colors } from '@/styles';
import { formatRoundedAmount } from '@/utils/currency';

import { discoverySummary, type DiscoveryInsight } from '../discoveryModel';
import type { DiagnosisCooldown } from '../diagnosisCooldown';

import { DiscoveryInsightRow } from './DiscoveryInsightRow';
import { styles } from './DiscoveryHome.styles';

type DiscoveryHomeProps = {
  diagnosis: { completedAt: string; issueCount: number } | null;
  insights: DiscoveryInsight[];
  isLoading: boolean;
  isRefreshing: boolean;
  isRunningAnalysis: boolean;
  error: string | null;
  cooldown: DiagnosisCooldown;
  canRetryRecommendations: boolean;
  load: () => Promise<void>;
  refetch: () => Promise<void>;
  runAnalysis: (forceRefresh?: boolean) => Promise<void>;
  retryRecommendationGeneration: () => Promise<void>;
  onBack: () => void;
  onInsightPress: (insightId: string) => void;
  onViewAll: () => void;
};

const GRADIENT_TOP_OFFSET = 190;

export function DiscoveryHome({
  canRetryRecommendations,
  cooldown,
  diagnosis,
  error,
  insights,
  isLoading,
  isRefreshing,
  isRunningAnalysis,
  load,
  onBack,
  onInsightPress,
  onViewAll,
  refetch,
  retryRecommendationGeneration,
  runAnalysis,
}: DiscoveryHomeProps) {
  const [gradientContainerHeight, setGradientContainerHeight] = useState(0);
  // 서버 요약은 화면에서 거르는 목표 위험까지 세므로, 실제로 뜬 발견에서 다시 집계한다.
  const shownSummary = discoverySummary(insights);

  const handleGradientContainerLayout = (event: LayoutChangeEvent) => {
    setGradientContainerHeight(event.nativeEvent.layout.height);
  };

  return (
    <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
      <AppHeader onBackPress={onBack} showBackButton title="발견" />

      <RefreshableBouncyScrollView
        contentContainerStyle={styles.scrollContent}
        onRefresh={() => { void refetch(); }}
        refreshing={isRefreshing}
        style={styles.scroll}>
        <View style={styles.titleGroup}>
          <Text style={styles.subtitle}>
            지금 확인할 발견 내용{' '}
            <Text style={styles.subtitleHighlight}>{insights.length}건</Text>
          </Text>
        </View>

        <View onLayout={handleGradientContainerLayout} style={styles.gradientContainer}>
          {gradientContainerHeight > GRADIENT_TOP_OFFSET ? (
            <LinearGradient
              colors={[colors.background, colors.home.goalGradientEnd]}
              style={[
                styles.gradientOverlay,
                { height: gradientContainerHeight - GRADIENT_TOP_OFFSET },
              ]}
            />
          ) : null}

          <View style={styles.summaryShowcase}>
            <View style={[styles.sideSummaryCard, styles.leftSummaryCard]}>
              <Image
                contentFit="contain"
                source={require('@/assets/images/discovery/discovery_side2.png')}
                style={styles.sideSummaryImage}
              />
            </View>
            <View style={[styles.sideSummaryCard, styles.rightSummaryCard]}>
              <Image
                contentFit="contain"
                source={require('@/assets/images/discovery/discovery_side1.png')}
                style={styles.sideSummaryImage}
              />
            </View>

            <View style={styles.summaryCard}>
              <Image
                contentFit="contain"
                source={require('@/assets/icons/common/money.png')}
                style={styles.summaryIcon}
              />
              <Text style={styles.summaryText}>
                {/*
                  서버 issueCount는 화면에서 거르는 목표 위험까지 세므로 실제 목록 길이를 쓴다.
                  보여 줄 결과가 아직 없을 때만 확인 중 문구를 쓴다. 새로고침 중에 문구를 바꾸면
                  이미 나온 건수가 사라졌다 돌아오는 것처럼 보인다.
                */}
                {isLoading && diagnosis === null
                  ? '진단 결과를 확인하고 있어요'
                  : `진단에서 ${insights.length}건 발견했어요`}
              </Text>
              <Text style={styles.summarySubtext}>
                {shownSummary.annualSavingAmount !== null
                  ? `연 ${formatRoundedAmount(shownSummary.annualSavingAmount)}원 절약 예상`
                  : '연 절감액 산정 전'}
                {shownSummary.goalAdvanceDays !== null
                  ? ` · 최대 ${shownSummary.goalAdvanceDays}일 단축`
                  : ''}
              </Text>
            </View>

            <View style={styles.illustrationWrap}>
              <Image
                accessibilityLabel="돋보기를 든 도리와 토리"
                contentFit="contain"
                source={require('@/assets/images/discovery/discovery_list.png')}
                style={styles.squirrelImage}
              />
              <Image
                contentFit="contain"
                source={require('@/assets/images/discovery/discovery_list2.png')}
                style={styles.glassSticker}
              />
            </View>
          </View>

          <View style={styles.insightSection}>
            <View style={styles.sectionHeaderRow}>
              <Text style={styles.sectionTitle}>지금 확인해볼 것</Text>
              {insights.length > 0 ? (
                <Pressable
                  accessibilityLabel="전체 보기"
                  accessibilityRole="button"
                  hitSlop={8}
                  onPress={onViewAll}>
                  <Text style={styles.viewAllText}>전체 보기 ›</Text>
                </Pressable>
              ) : null}
            </View>
            {error ? <Text style={styles.footnote}>{error}</Text> : null}
            <View style={styles.insightListCard}>
              {insights.slice(0, 4).map((insight, index, items) => (
                <DiscoveryInsightRow
                  insight={insight}
                  isLast={index === items.length - 1}
                  key={insight.id}
                  onPress={() => onInsightPress(insight.id)}
                />
              ))}
              {!isLoading && insights.length === 0 && !error ? (
                <Text style={styles.footnote}>
                  현재 진단에서 발견된 항목이 없어요. 분석되지 않은 항목은 제외됩니다.
                </Text>
              ) : null}
            </View>

            <Text style={styles.footnote}>
              {diagnosis
                ? `분석 시점: ${new Date(diagnosis.completedAt).toLocaleDateString('ko-KR')}`
                : '아직 완료된 진단이 없어요.'}
            </Text>
          </View>

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
                styles.viewAllButton,
                isRunningAnalysis && styles.viewAllButtonDisabled,
              ]}>
              <Text style={styles.viewAllButtonLabel}>
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

          {diagnosis ? (
            <View style={styles.diagnosisFooter}>
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
                  styles.diagnosisAction,
                  (isLoading || !cooldown.canRun) &&
                    styles.diagnosisActionDisabled,
                  pressed &&
                    !isLoading &&
                    cooldown.canRun &&
                    styles.diagnosisActionPressed,
                ]}>
                <Text
                  style={[
                    styles.diagnosisActionText,
                    (isLoading || !cooldown.canRun) &&
                      styles.diagnosisActionTextDisabled,
                  ]}>
                  {isRunningAnalysis ? '진단 중...' : '새로 진단하기'}
                </Text>
              </Pressable>
            </View>
          ) : null}

        </View>
      </RefreshableBouncyScrollView>
    </SafeAreaLayout>
  );
}
