import { useRouter } from 'expo-router';
import { LinearGradient } from 'expo-linear-gradient';
import { useState } from 'react';
import { type LayoutChangeEvent, Text, View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { DISCOVERY_INSIGHTS, DISCOVERY_SUMMARY } from '@/mocks/discovery';
import { colors } from '@/styles';
import { formatNumber } from '@/utils/currency';

import { DiscoveryInsightsCard } from './components/DiscoveryInsightsCard';
import { styles } from './DiscoveryInsightsScreen.styles';

export function DiscoveryInsightsScreen() {
  const router = useRouter();
  const [summaryCardHeight, setSummaryCardHeight] = useState(0);
  const [gradientContainerHeight, setGradientContainerHeight] = useState(0);

  const handleSummaryCardLayout = (event: LayoutChangeEvent) => {
    setSummaryCardHeight(event.nativeEvent.layout.height);
  };

  const handleGradientContainerLayout = (event: LayoutChangeEvent) => {
    setGradientContainerHeight(event.nativeEvent.layout.height);
  };

  return (
    <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
      <AppHeader showBackButton title="발견" />

      <RefreshableBouncyScrollView contentContainerStyle={styles.scrollContent}>
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
              <Text style={styles.summaryCount}>{DISCOVERY_INSIGHTS.length}건</Text>
            </View>

            <Text style={styles.summaryHeadline}>
              다 바꾸면 연{' '}
              <Text style={styles.summaryAmountHighlight}>
                {formatNumber(DISCOVERY_SUMMARY.totalAnnualSavingAmount)}원
              </Text>
              이{'\n'}내 쪽으로 와요
            </Text>

            <View style={styles.summaryPill}>
              <Text style={styles.summaryPillLabel}>다 바꾸면? 모으기 목표일 단축</Text>
              <Text style={styles.summaryPillValue}>- {DISCOVERY_SUMMARY.goalAccelerationDays}일</Text>
            </View>
          </View>

          <View style={styles.listSection}>
            <Text style={styles.sectionTitle}>발견한 내용 {DISCOVERY_INSIGHTS.length}개</Text>

            {DISCOVERY_INSIGHTS.map((insight) => (
              <DiscoveryInsightsCard
                insight={insight}
                key={insight.id}
                onPress={() => router.push(routes.discoveryInsightDetail(insight.id))}
              />
            ))}
          </View>
        </View>
      </RefreshableBouncyScrollView>
    </SafeAreaLayout>
  );
}
