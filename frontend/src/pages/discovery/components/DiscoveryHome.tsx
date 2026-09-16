import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useState } from 'react';
import { type LayoutChangeEvent, Pressable, Text, View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { colors } from '@/styles';

import type { DiscoveryInsight } from '../discoveryModel';
import type { DiagnosisCooldown } from '../diagnosisCooldown';

import { DiscoveryInsightRow } from './DiscoveryInsightRow';
import { styles } from './DiscoveryHome.styles';

type DiscoveryHomeProps = {
  diagnosis: { completedAt: string } | null;
  insights: DiscoveryInsight[];
  isLoading: boolean;
  error: string | null;
  cooldown: DiagnosisCooldown;
  canRetryRecommendations: boolean;
  load: () => Promise<void>;
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
  load,
  onBack,
  onInsightPress,
  onViewAll,
  retryRecommendationGeneration,
  runAnalysis,
}: DiscoveryHomeProps) {
  const [gradientContainerHeight, setGradientContainerHeight] = useState(0);

  const handleGradientContainerLayout = (event: LayoutChangeEvent) => {
    setGradientContainerHeight(event.nativeEvent.layout.height);
  };

  return (
    <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
      <AppHeader onBackPress={onBack} showBackButton title="발견" />

      <RefreshableBouncyScrollView
        contentContainerStyle={styles.scrollContent}
        onRefresh={() => { void load(); }}
        refreshing={isLoading}
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
                {isLoading
                  ? '진단 결과를 확인하고 있어요'
                  : `지금 확인할 내용 ${insights.length}개`}
              </Text>
              <Text style={styles.summarySubtext}>
                각 항목에서 추천과 예상 효과를 확인해 보세요
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
            <Text style={styles.sectionTitle}>지금 확인해볼 것</Text>
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
              style={styles.viewAllButton}>
              <Text style={styles.viewAllButtonLabel}>
                {canRetryRecommendations
                  ? '추천 생성 다시 시도'
                  : error
                    ? '다시 시도하기'
                    : '진단 시작하기'}
              </Text>
            </Pressable>
          ) : null}

          {diagnosis && !isLoading ? (
            <View>
              <Pressable
                accessibilityRole="button"
                disabled={!cooldown.canRun}
                onPress={() => { void runAnalysis(true); }}
                style={[styles.viewAllButton, !cooldown.canRun && styles.disabledButton]}>
                <Text style={styles.viewAllButtonLabel}>새로 진단하기</Text>
              </Pressable>
              {cooldown.label ? <Text style={styles.cooldownLabel}>{cooldown.label}</Text> : null}
            </View>
          ) : null}

          {insights.length > 0 ? <Pressable
            accessibilityLabel="전체 보기"
            accessibilityRole="button"
            onPress={onViewAll}
            style={({ pressed }) => [styles.viewAllButton, pressed && styles.viewAllButtonPressed]}>
            <Text style={styles.viewAllButtonLabel}>전체 보기</Text>
          </Pressable> : null}
        </View>
      </RefreshableBouncyScrollView>
    </SafeAreaLayout>
  );
}
