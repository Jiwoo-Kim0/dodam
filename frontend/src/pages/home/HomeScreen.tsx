import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useRouter } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Text, View } from 'react-native';

import {
  BouncyScrollView,
  type BouncyScrollViewHandle,
} from '@/components/bouncyScrollView/BouncyScrollView';
import { subscribeHomeTabReselect } from '@/components/bottomNavigation/homeTabReselectEvents';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { colors } from '@/styles';

import { AssetSummaryCard } from './components/AssetSummaryCard';
import { BriefingCard } from './components/BriefingCard';
import { ExpenseSummaryCard } from './components/ExpenseSummaryCard';
import { GoalCarousel } from './components/GoalCarousel';
import { HomeHeader } from './components/HomeHeader';
import { SpendingTopList } from './components/SpendingTopList';
import { styles } from './HomeScreen.styles';

/** 로그인 연동 전까지 사용하는 임시 사용자명이다. */
const MOCK_USER_NAME = '싸피';
const MOCK_REFRESH_DURATION = 600;

export function HomeScreen() {
  const router = useRouter();
  const scrollRef = useRef<BouncyScrollViewHandle>(null);
  const [sectionSize, setSectionSize] = useState({ width: 0, height: 0 });
  const [isRefreshing, setIsRefreshing] = useState(false);
  const refreshTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => {
    if (refreshTimeoutRef.current) {
      clearTimeout(refreshTimeoutRef.current);
    }
  }, []);

  const handleHomeRefresh = useCallback(() => {
    if (isRefreshing) return;

    scrollRef.current?.scrollToTop();
    setIsRefreshing(true);

    // API 연결 전에는 새로고침 동작을 확인할 수 있도록 짧은 피드백만 제공한다.
    refreshTimeoutRef.current = setTimeout(() => {
      setIsRefreshing(false);
      refreshTimeoutRef.current = null;
    }, MOCK_REFRESH_DURATION);
  }, [isRefreshing]);

  useEffect(() => subscribeHomeTabReselect(handleHomeRefresh), [handleHomeRefresh]);

  return (
    <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
      <HomeHeader onLogoPress={handleHomeRefresh} />

      <BouncyScrollView
        contentContainerStyle={styles.scrollContent}
        onRefresh={handleHomeRefresh}
        ref={scrollRef}
        refreshBackgroundColor={colors.white}
        refreshIndicatorColor={colors.primary.green}
        refreshing={isRefreshing}
        style={styles.scroll}>
        <View
          onLayout={(event) =>
            setSectionSize({
              width: event.nativeEvent.layout.width,
              height: event.nativeEvent.layout.height,
            })
          }
          style={styles.goalSection}>
          <LinearGradient
            // 디자인 PDF/Figma에 지정된 그라데이션 값 (#FBFBFB → #F3F6E3), 이 배경에서만 사용한다.
            colors={[
              colors.white,
              colors.white,
              colors.home.goalGradientEnd,
            ]}
            locations={[0, 0.32, 1]}
            style={styles.goalSectionBackground}
          />
          {sectionSize.height > 0 ? (
            <Image
              contentFit="contain"
              contentPosition={{ bottom: 0, right: 0 }}
              source={require('@/assets/images/home/goal_section_background.png')}
              style={[styles.treeIllustration, sectionSize]}
            />
          ) : null}

          <View style={styles.greeting}>
            <Text style={styles.greetingText}>
              <Text style={styles.greetingName}>{`${MOCK_USER_NAME}님`}</Text>
              {', 좋은 아침이에요'}
            </Text>
            <Text style={styles.greetingHeadline}>오늘도 목표를{'\n'}향해 한 걸음 더</Text>
          </View>

          <GoalCarousel />
        </View>

        <View style={styles.body}>
          <ExpenseSummaryCard onDetailPress={() => router.push(routes.ledger)} />
          <BriefingCard onDetailPress={() => router.push(routes.dailyBriefing)} />
          <AssetSummaryCard onDetailPress={() => router.push(routes.assets)} />
          <SpendingTopList />
        </View>
      </BouncyScrollView>
    </SafeAreaLayout>
  );
}
