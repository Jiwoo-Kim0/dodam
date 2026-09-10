import type { PropsWithChildren } from 'react';
import { useEffect, useState } from 'react';
import type { StyleProp, ViewStyle } from 'react-native';
import { AccessibilityInfo, ScrollView, StyleSheet } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withDecay,
  withSpring,
} from 'react-native-reanimated';

import {
  DECAY_DECELERATION,
  RUBBER_BAND_RESISTANCE,
  SPRING_BACK_CONFIG,
  withRubberBand,
} from './scrollPhysics';

type BouncyScrollViewProps = PropsWithChildren<{
  style?: StyleProp<ViewStyle>;
  contentContainerStyle?: StyleProp<ViewStyle>;
}>;

/**
 * Android에서도 iOS처럼 경계 너머로 끌면 콘텐츠가 저항하며 따라오고,
 * 손을 놓으면 원래 위치로 돌아오는 세로 스크롤 컨테이너다.
 */
export function BouncyScrollView({
  children,
  style,
  contentContainerStyle,
}: BouncyScrollViewProps) {
  const [containerHeight, setContainerHeight] = useState(0);
  const [contentHeight, setContentHeight] = useState(0);
  const [isScreenReaderEnabled, setIsScreenReaderEnabled] = useState(false);

  const translateY = useSharedValue(0);
  const dragStartY = useSharedValue(0);

  const maxScroll = Math.max(0, contentHeight - containerHeight);

  useEffect(() => {
    let isMounted = true;

    void AccessibilityInfo.isScreenReaderEnabled().then((enabled) => {
      if (isMounted) {
        setIsScreenReaderEnabled(enabled);
      }
    });

    const subscription = AccessibilityInfo.addEventListener(
      'screenReaderChanged',
      setIsScreenReaderEnabled,
    );

    return () => {
      isMounted = false;
      subscription.remove();
    };
  }, []);

  const panGesture = Gesture.Pan()
    .activeOffsetY([-10, 10])
    .failOffsetX([-15, 15])
    .onStart(() => {
      dragStartY.value = translateY.value;
    })
    .onUpdate((event) => {
      const next = dragStartY.value + event.translationY;
      translateY.value = withRubberBand(next, -maxScroll, 0, RUBBER_BAND_RESISTANCE);
    })
    .onEnd((event) => {
      if (translateY.value > 0) {
        translateY.value = withSpring(0, SPRING_BACK_CONFIG);
        return;
      }

      if (translateY.value < -maxScroll) {
        translateY.value = withSpring(-maxScroll, SPRING_BACK_CONFIG);
        return;
      }

      translateY.value = withDecay({
        velocity: event.velocityY,
        deceleration: DECAY_DECELERATION,
        clamp: [-maxScroll, 0],
      });
    });

  const animatedContentStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: translateY.value }],
  }));

  if (isScreenReaderEnabled) {
    return (
      <ScrollView contentContainerStyle={contentContainerStyle} style={[styles.container, style]}>
        {children}
      </ScrollView>
    );
  }

  return (
    <GestureDetector gesture={panGesture}>
      <Animated.View
        onLayout={(event) => setContainerHeight(event.nativeEvent.layout.height)}
        style={[styles.container, style]}>
        <Animated.View
          onLayout={(event) => setContentHeight(event.nativeEvent.layout.height)}
          style={[contentContainerStyle, animatedContentStyle]}>
          {children}
        </Animated.View>
      </Animated.View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    overflow: 'hidden',
  },
});
