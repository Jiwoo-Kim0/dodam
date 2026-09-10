import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Text, View } from 'react-native';

import { BouncyScrollView } from '@/components/bouncyScrollView/BouncyScrollView';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { colors } from '@/styles';

import { BriefingCard } from './components/BriefingCard';
import { GoalCarousel } from './components/GoalCarousel';
import { HomeHeader } from './components/HomeHeader';
import { SpendingTopList } from './components/SpendingTopList';
import { AssetSummaryCard } from './components/AssetSummaryCard';
import { ExpenseSummaryCard } from './components/ExpenseSummaryCard';
import { styles } from './HomeScreen.styles';

/** 로그인 연동 전까지 사용하는 임시 사용자명이다. */
const MOCK_USER_NAME = '싸피';

export function HomeScreen() {
  const router = useRouter();
  const [sectionSize, setSectionSize] = useState({ width: 0, height: 0 });

  return (
    <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
      <HomeHeader />

      <BouncyScrollView contentContainerStyle={styles.scrollContent}>
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
            colors={[colors.background, colors.home.goalGradientEnd]}
            style={styles.goalSectionBackground}
          />
          {sectionSize.height > 0 ? (
            <Image
              contentFit="contain"
              contentPosition={{ bottom: 22, right: 0 }}
              source={require('@/assets/images/home/goal_section_background.png')}
              style={[styles.treeIllustration, sectionSize]}
            />
          ) : null}

          <View style={styles.greeting}>
            <Text style={styles.greetingText}>{`${MOCK_USER_NAME}님, 좋은 아침이에요`}</Text>
            <Text style={styles.greetingHeadline}>오늘도 목표를{'\n'}향해 한 걸음 더</Text>
          </View>

          <GoalCarousel />
        </View>

        <View style={styles.body}>
          <ExpenseSummaryCard onDetailPress={() => router.push(routes.ledger)} />
          {/* 도담 브리핑 전용 화면은 추후 별도로 만들 예정이라 아직 라우팅을 연결하지 않는다. */}
          <BriefingCard onDetailPress={() => {}} />
          <AssetSummaryCard onDetailPress={() => router.push(routes.assets)} />
          <SpendingTopList />
        </View>
      </BouncyScrollView>
    </SafeAreaLayout>
  );
}
