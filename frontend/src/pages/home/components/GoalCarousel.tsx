import { useState } from 'react';
import { useRouter } from 'expo-router';
import type { AccessibilityActionEvent, LayoutChangeEvent } from 'react-native';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  runOnJS,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';

import {
  RUBBER_BAND_RESISTANCE,
  SPRING_BACK_CONFIG,
  withRubberBand,
} from '@/components/bouncyScrollView/scrollPhysics';
import { routes } from '@/constants/routes';
import { colors, semanticSpacing, spacing } from '@/styles';
import type { HomeGoal } from '@/types/goal.types';

import { GoalCard } from './GoalCard';
import { styles } from './GoalCarousel.styles';

const NEXT_CARD_PEEK = spacing[3];
const PAGE_TRAILING_SPACE = semanticSpacing.screenMargin + NEXT_CARD_PEEK;

interface GoalCarouselProps {
  goals: readonly HomeGoal[];
  hasError: boolean;
  isLoading: boolean;
}

export function GoalCarousel({ goals, hasError, isLoading }: GoalCarouselProps) {
  const router = useRouter();
  const [pageWidth, setPageWidth] = useState(0);
  const [activeIndex, setActiveIndex] = useState(0);
  const translateX = useSharedValue(0);
  const dragStartX = useSharedValue(0);

  const maxIndex = Math.max(0, goals.length - 1);
  const maxScroll = maxIndex * pageWidth;

  const handleLayout = (event: LayoutChangeEvent) => {
    const width = Math.max(0, event.nativeEvent.layout.width - PAGE_TRAILING_SPACE);
    setPageWidth(width);
    translateX.value = -activeIndex * width;
  };

  const updateActiveIndex = (index: number) => {
    setActiveIndex(index);
  };

  const snapToPage = (index: number) => {
    const nextIndex = Math.min(maxIndex, Math.max(0, index));
    translateX.value = withSpring(-nextIndex * pageWidth, SPRING_BACK_CONFIG);
    setActiveIndex(nextIndex);
  };

  const openActiveGoal = () => {
    const goal = goals[activeIndex];

    if (goal) {
      router.push(routes.goalDetail(goal.id));
    }
  };

  const handleAccessibilityAction = (event: AccessibilityActionEvent) => {
    if (event.nativeEvent.actionName === 'increment') {
      snapToPage(activeIndex + 1);
    }

    if (event.nativeEvent.actionName === 'decrement') {
      snapToPage(activeIndex - 1);
    }

    if (event.nativeEvent.actionName === 'activate') {
      openActiveGoal();
    }
  };

  const panGesture = Gesture.Pan()
    .activeOffsetX([-10, 10])
    .failOffsetY([-15, 15])
    .onStart(() => {
      cancelAnimation(translateX);
      dragStartX.value = translateX.value;
    })
    .onUpdate((event) => {
      const next = dragStartX.value + event.translationX;
      translateX.value = withRubberBand(next, -maxScroll, 0, RUBBER_BAND_RESISTANCE);
    })
    .onEnd((event) => {
      if (pageWidth === 0) {
        return;
      }

      const projectedX = translateX.value + event.velocityX * 0.12;
      const nextIndex = Math.min(
        maxIndex,
        Math.max(0, Math.round(-projectedX / pageWidth)),
      );

      translateX.value = withSpring(-nextIndex * pageWidth, SPRING_BACK_CONFIG);
      runOnJS(updateActiveIndex)(nextIndex);
    });

  const tapGesture = Gesture.Tap()
    .maxDistance(10)
    .onEnd((_event, success) => {
      if (success) {
        runOnJS(openActiveGoal)();
      }
    });

  const carouselGesture = Gesture.Race(panGesture, tapGesture);

  const animatedTrackStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: translateX.value }],
  }));

  if (isLoading) {
    return (
      <View style={[styles.container, styles.status]}>
        <ActivityIndicator color={colors.primary.green} />
      </View>
    );
  }

  if (goals.length === 0) {
    return (
      <View style={[styles.container, styles.status]}>
        <Text style={styles.statusText}>
          {hasError ? '목표를 불러오지 못했어요' : '설정된 목표가 없습니다'}
        </Text>
        {/* 조회 실패는 목표가 없는 것과 다르다. 이때 목표를 새로 만들게 안내하면 중복 생성이 된다. */}
        {!hasError ? (
          <Pressable
            accessibilityLabel="목표 추가하기"
            accessibilityRole="button"
            hitSlop={8}
            onPress={() => router.push(routes.goalSetup)}
            style={({ pressed }) => pressed && styles.addGoalPressed}>
            <Text style={styles.addGoalLabel}>목표 추가하기</Text>
          </Pressable>
        ) : null}
      </View>
    );
  }

  return (
    <View
      onLayout={handleLayout}
      style={styles.container}>
      <View style={styles.headerActions}>
        <Pressable
          accessibilityLabel="새 목표 추가"
          accessibilityRole="button"
          hitSlop={8}
          onPress={() => router.push(routes.goalSetup)}
          style={({ pressed }) => [
            styles.headerAddButton,
            pressed && styles.addGoalPressed,
          ]}>
          <Text style={styles.headerAddIcon}>+</Text>
          <Text style={styles.headerAddLabel}>목표 추가</Text>
        </Pressable>
      </View>
      {pageWidth > 0 ? (
        <GestureDetector gesture={carouselGesture}>
          <View style={styles.viewport}>
            <Animated.View style={[styles.track, animatedTrackStyle]}>
              {goals.map((goal) => (
                <View key={goal.id} style={[styles.page, { width: pageWidth }]}>
                  <GoalCard goal={goal} />
                </View>
              ))}
            </Animated.View>
          </View>
        </GestureDetector>
      ) : null}

      <View
        accessibilityActions={[
          { name: 'activate', label: '목표 상세 보기' },
          { name: 'increment', label: '다음 목표' },
          { name: 'decrement', label: '이전 목표' },
        ]}
        accessibilityHint="좌우로 쓸어 목표 카드를 전환합니다"
        accessibilityLabel="목표 카드 선택"
        accessibilityRole="adjustable"
        accessibilityValue={{
          max: goals.length,
          min: 1,
          now: activeIndex + 1,
          text: goals[activeIndex]?.title,
        }}
        accessible
        onAccessibilityAction={handleAccessibilityAction}
        style={styles.dots}>
        {goals.map((goal, index) => (
          <View
            key={goal.id}
            style={[styles.dot, index === activeIndex && styles.dotActive]}
          />
        ))}
      </View>
    </View>
  );
}
