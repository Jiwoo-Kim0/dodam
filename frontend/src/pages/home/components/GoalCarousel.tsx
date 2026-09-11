import { useState } from 'react';
import { useRouter } from 'expo-router';
import type { AccessibilityActionEvent, LayoutChangeEvent } from 'react-native';
import { View } from 'react-native';
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
import { HOME_GOALS } from '@/mocks/home';
import { semanticSpacing, spacing } from '@/styles';

import { GoalCard } from './GoalCard';
import { styles } from './GoalCarousel.styles';

const NEXT_CARD_PEEK = spacing[3];
const PAGE_TRAILING_SPACE = semanticSpacing.screenMargin + NEXT_CARD_PEEK;

export function GoalCarousel() {
  const router = useRouter();
  const [pageWidth, setPageWidth] = useState(0);
  const [activeIndex, setActiveIndex] = useState(0);
  const translateX = useSharedValue(0);
  const dragStartX = useSharedValue(0);

  const maxIndex = Math.max(0, HOME_GOALS.length - 1);
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
    const goal = HOME_GOALS[activeIndex];

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

  return (
    <View
      onLayout={handleLayout}
      style={styles.container}>
      {pageWidth > 0 ? (
        <GestureDetector gesture={carouselGesture}>
          <View style={styles.viewport}>
            <Animated.View style={[styles.track, animatedTrackStyle]}>
              {HOME_GOALS.map((goal) => (
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
          max: HOME_GOALS.length,
          min: 1,
          now: activeIndex + 1,
          text: HOME_GOALS[activeIndex]?.title,
        }}
        accessible
        onAccessibilityAction={handleAccessibilityAction}
        style={styles.dots}>
        {HOME_GOALS.map((goal, index) => (
          <View
            key={goal.id}
            style={[styles.dot, index === activeIndex && styles.dotActive]}
          />
        ))}
      </View>
    </View>
  );
}
