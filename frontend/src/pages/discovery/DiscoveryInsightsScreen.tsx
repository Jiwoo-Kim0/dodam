import { useRouter } from 'expo-router';
import { LinearGradient } from 'expo-linear-gradient';
import { SymbolView } from 'expo-symbols';
import { useState } from 'react';
import { type LayoutChangeEvent, Pressable, Text, View } from 'react-native';

import { BouncyScrollView } from '@/components/bouncyScrollView/BouncyScrollView';
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
      <View style={styles.header}>
        <Pressable
          accessibilityLabel="이전 화면으로 이동"
          accessibilityRole="button"
          hitSlop={8}
          onPress={() => router.back()}
          style={({ pressed }) => [styles.backButton, pressed && styles.backButtonPressed]}>
          <SymbolView
            name={{ android: 'arrow_back', ios: 'chevron.left', web: 'arrow_back' }}
            size={24}
            tintColor={colors.textPrimary}
          />
        </Pressable>
      </View>

      <BouncyScrollView contentContainerStyle={styles.scrollContent}>
        <Text style={styles.title}>발견</Text>

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
              <DiscoveryInsightsCard insight={insight} key={insight.id} />
            ))}
          </View>
        </View>
      </BouncyScrollView>
    </SafeAreaLayout>
  );
}
