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
import { useRefreshFeedback } from '@/hooks/useRefreshFeedback';
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

export function HomeScreen() {
  const router = useRouter();
  const scrollRef = useRef<BouncyScrollViewHandle>(null);
  const [sectionSize, setSectionSize] = useState({ width: 0, height: 0 });
  const { isRefreshing, triggerRefresh } = useRefreshFeedback();

  const handleHomeRefresh = useCallback(() => {
    scrollRef.current?.scrollToTop();
    triggerRefresh();
  }, [triggerRefresh]);

  useEffect(() => subscribeHomeTabReselect(handleHomeRefresh), [handleHomeRefresh]);

  return (
    <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
      <HomeHeader
        onLogoPress={handleHomeRefresh}
        onNotificationPress={() => router.push(routes.notifications)}
      />

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
            // 상단의 흰 배경을 유지한 뒤 목표 영역에서 연두색으로 자연스럽게 전환한다.
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
