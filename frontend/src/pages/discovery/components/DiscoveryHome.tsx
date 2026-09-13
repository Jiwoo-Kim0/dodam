import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useState } from 'react';
import { type LayoutChangeEvent, Pressable, Text, View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { DISCOVERY_INSIGHTS, DISCOVERY_SUMMARY } from '@/mocks/discovery';
import { colors } from '@/styles';
import { formatNumber } from '@/utils/currency';

import { DiscoveryInsightRow } from './DiscoveryInsightRow';
import { styles } from './DiscoveryHome.styles';

type DiscoveryHomeProps = {
  onBack: () => void;
  onViewAll: () => void;
};

const GRADIENT_TOP_OFFSET = 190;

export function DiscoveryHome({ onBack, onViewAll }: DiscoveryHomeProps) {
  const [gradientContainerHeight, setGradientContainerHeight] = useState(0);

  const handleGradientContainerLayout = (event: LayoutChangeEvent) => {
    setGradientContainerHeight(event.nativeEvent.layout.height);
  };

  return (
    <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
      <AppHeader onBackPress={onBack} showBackButton title="발견" />

      <RefreshableBouncyScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.titleGroup}>
          <Text style={styles.subtitle}>
            {DISCOVERY_SUMMARY.userName}님의 절약 잠재력{' '}
            <Text style={styles.subtitleHighlight}>{DISCOVERY_INSIGHTS.length}건</Text>
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
                다 바꾸면 연{' '}
                <Text style={styles.summaryAmountHighlight}>
                  {formatNumber(DISCOVERY_SUMMARY.totalAnnualSavingAmount)}
                </Text>
                원 절약
              </Text>
              <Text style={styles.summarySubtext}>
                목표를 약{' '}
                <Text style={styles.summaryDaysHighlight}>
                  {DISCOVERY_SUMMARY.goalAccelerationDays}일
                </Text>{' '}
                앞당길 수 있어요
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
            <View style={styles.insightListCard}>
              {DISCOVERY_INSIGHTS.map((insight, index) => (
                <DiscoveryInsightRow
                  insight={insight}
                  isLast={index === DISCOVERY_INSIGHTS.length - 1}
                  key={insight.id}
                />
              ))}
            </View>

            <Text style={styles.footnote}>
              위 분석은 {DISCOVERY_SUMMARY.analysisPeriodLabel} 데이터를 기준으로 했어요
            </Text>
          </View>

          <Pressable
            accessibilityLabel="전체 보기"
            accessibilityRole="button"
            onPress={onViewAll}
            style={({ pressed }) => [styles.viewAllButton, pressed && styles.viewAllButtonPressed]}>
            <Text style={styles.viewAllButtonLabel}>전체 보기</Text>
          </Pressable>
        </View>
      </RefreshableBouncyScrollView>
    </SafeAreaLayout>
  );
}
