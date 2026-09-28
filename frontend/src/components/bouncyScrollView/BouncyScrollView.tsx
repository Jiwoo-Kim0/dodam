import type { PropsWithChildren } from 'react';
import {
  forwardRef,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from 'react';
import type { NativeScrollEvent, NativeSyntheticEvent, StyleProp, ViewStyle } from 'react-native';
import {
  AccessibilityInfo,
  ActivityIndicator,
  RefreshControl,
  ScrollView,
  StyleSheet,
} from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Extrapolation,
  interpolate,
  runOnJS,
  useAnimatedReaction,
  useAnimatedStyle,
  useSharedValue,
  withDecay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

import {
  DECAY_DECELERATION,
  RUBBER_BAND_RESISTANCE,
  SPRING_BACK_CONFIG,
  withRubberBand,
} from './scrollPhysics';

const END_REACHED_RESET_MARGIN = 40;
const SCROLL_OFFSET_REPORT_DELTA = 16;
const DEFAULT_REFRESH_THRESHOLD = 44;
const REFRESH_HOLD_OFFSET = 48;

type BouncyScrollViewProps = PropsWithChildren<{
  style?: StyleProp<ViewStyle>;
  contentContainerStyle?: StyleProp<ViewStyle>;
  onEndReached?: () => void;
  endReachedThreshold?: number;
  onScrollOffsetChange?: (offsetY: number) => void;
  /** true면 드래그가 끝날 때 가장 가까운 pageSize 배수 오프셋으로 스냅한다. */
  pagingEnabled?: boolean;
  pageSize?: number;
  onRefresh?: () => void;
  refreshing?: boolean;
  refreshIndicatorColor?: string;
  refreshBackgroundColor?: string;
  refreshThreshold?: number;
}>;

export interface BouncyScrollViewHandle {
  scrollToTop: () => void;
  scrollToOffset: (offset: number) => void;
}

/**
 * Android에서도 iOS처럼 경계 너머로 끌면 콘텐츠가 저항하며 따라오고,
 * 손을 놓으면 원래 위치로 돌아오는 세로 스크롤 컨테이너다.
 */
export const BouncyScrollView = forwardRef<
  BouncyScrollViewHandle,
  BouncyScrollViewProps
>(function BouncyScrollView(
  {
    children,
    style,
    contentContainerStyle,
    onEndReached,
    endReachedThreshold = 240,
    onScrollOffsetChange,
    pagingEnabled = false,
    pageSize = 0,
    onRefresh,
    refreshing = false,
    refreshIndicatorColor,
    refreshBackgroundColor,
    refreshThreshold = DEFAULT_REFRESH_THRESHOLD,
  },
  ref,
) {
  const [containerHeight, setContainerHeight] = useState(0);
  const [contentHeight, setContentHeight] = useState(0);
  const [isScreenReaderEnabled, setIsScreenReaderEnabled] = useState(false);
  const nativeScrollRef = useRef<ScrollView>(null);
  const hasFiredNativeEndReached = useRef(false);
  const nativeOffsetRef = useRef(0);
  const nativeLayoutHeightRef = useRef(0);

  const translateY = useSharedValue(0);
  const dragStartY = useSharedValue(0);
  const hasFiredEndReached = useSharedValue(false);
  const lastReportedOffset = useSharedValue(0);

  const maxScroll = Math.max(0, contentHeight - containerHeight);

  // translateY only changes in response to a gesture, so once the user is
  // already sitting at (or near) the bottom, appending a page doesn't move
  // it and the animated reaction below never re-fires. Re-check here too,
  // any time layout/content size changes (e.g. right after a page loads).
  useEffect(() => {
    if (!onEndReached || containerHeight <= 0) return;

    if (contentHeight <= containerHeight) {
      hasFiredEndReached.value = true;
      onEndReached();
      return;
    }

    const distanceFromBottom = maxScroll + translateY.value;
    if (distanceFromBottom <= endReachedThreshold) {
      hasFiredEndReached.value = true;
      onEndReached();
    } else {
      hasFiredEndReached.value = false;
    }
  }, [containerHeight, contentHeight, endReachedThreshold, hasFiredEndReached, maxScroll, onEndReached, translateY]);

  useImperativeHandle(ref, () => ({
    scrollToTop: () => {
      nativeScrollRef.current?.scrollTo({ animated: true, y: 0 });
      translateY.value = withTiming(0, { duration: 250 });
    },
    scrollToOffset: (offset: number) => {
      nativeScrollRef.current?.scrollTo({ animated: true, y: offset });
      translateY.value = withSpring(-offset, SPRING_BACK_CONFIG);
    },
  }));

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

  useEffect(() => {
    if (refreshing) {
      translateY.value = withSpring(REFRESH_HOLD_OFFSET, SPRING_BACK_CONFIG);
      return;
    }

    if (translateY.value > 0) {
      translateY.value = withSpring(0, SPRING_BACK_CONFIG);
    }
  }, [refreshing, translateY]);

  const panGesture = useMemo(
    () =>
      Gesture.Pan()
        .enabled(!refreshing && containerHeight > 0 && contentHeight > 0)
        .activeOffsetY([-10, 10])
        .failOffsetX([-15, 15])
        .onStart(() => {
          dragStartY.value = translateY.value;
        })
        .onUpdate((event) => {
          const next = dragStartY.value + event.translationY;
          translateY.value = withRubberBand(
            next,
            -maxScroll,
            0,
            RUBBER_BAND_RESISTANCE,
          );
        })
        .onEnd((event) => {
          if (onRefresh && translateY.value >= refreshThreshold) {
            translateY.value = withSpring(
              REFRESH_HOLD_OFFSET,
              SPRING_BACK_CONFIG,
            );
            runOnJS(onRefresh)();
            return;
          }

          if (translateY.value > 0) {
            translateY.value = withSpring(0, SPRING_BACK_CONFIG);
            return;
          }

          if (translateY.value < -maxScroll) {
            translateY.value = withSpring(-maxScroll, SPRING_BACK_CONFIG);
            return;
          }

          if (pagingEnabled && pageSize > 0) {
            const startPage = Math.round(-dragStartY.value / pageSize);
            const swipeThreshold = pageSize * 0.2;
            const flingThreshold = 500;

            let targetPage = startPage;
            if (
              event.translationY < -swipeThreshold ||
              event.velocityY < -flingThreshold
            ) {
              targetPage = startPage + 1;
            } else if (
              event.translationY > swipeThreshold ||
              event.velocityY > flingThreshold
            ) {
              targetPage = startPage - 1;
            }

            const maxPage = Math.round(maxScroll / pageSize);
            targetPage = Math.min(Math.max(targetPage, 0), maxPage);
            translateY.value = withSpring(
              -targetPage * pageSize,
              SPRING_BACK_CONFIG,
            );
            return;
          }

          translateY.value = withDecay({
            velocity: event.velocityY,
            deceleration: DECAY_DECELERATION,
            clamp: [-maxScroll, 0],
          });
        }),
    [
      containerHeight,
      contentHeight,
      dragStartY,
      maxScroll,
      onRefresh,
      pageSize,
      pagingEnabled,
      refreshThreshold,
      refreshing,
      translateY,
    ],
  );

  const animatedContentStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: translateY.value }],
  }));

  const refreshIndicatorStyle = useAnimatedStyle(() => ({
    opacity: interpolate(
      translateY.value,
      [0, REFRESH_HOLD_OFFSET],
      [0, 1],
      Extrapolation.CLAMP,
    ),
    transform: [
      {
        translateY: interpolate(
          translateY.value,
          [0, REFRESH_HOLD_OFFSET],
          [-REFRESH_HOLD_OFFSET, 0],
          Extrapolation.CLAMP,
        ),
      },
    ],
  }));

  useAnimatedReaction(
    () => translateY.value,
    (current) => {
      if (!onEndReached || maxScroll <= 0) return;

      const distanceFromBottom = maxScroll + current;

      if (distanceFromBottom <= endReachedThreshold) {
        if (!hasFiredEndReached.value) {
          hasFiredEndReached.value = true;
          runOnJS(onEndReached)();
        }
      } else if (distanceFromBottom > endReachedThreshold + END_REACHED_RESET_MARGIN) {
        hasFiredEndReached.value = false;
      }
    },
    [maxScroll, onEndReached, endReachedThreshold],
  );

  useAnimatedReaction(
    () => translateY.value,
    (current) => {
      if (!onScrollOffsetChange) return;

      const offset = -current;
      if (Math.abs(offset - lastReportedOffset.value) >= SCROLL_OFFSET_REPORT_DELTA) {
        lastReportedOffset.value = offset;
        runOnJS(onScrollOffsetChange)(offset);
      }
    },
    [onScrollOffsetChange],
  );

  if (isScreenReaderEnabled) {
    const evaluateNativeEndReached = (contentSizeHeight: number) => {
      if (!onEndReached) return;

      const distanceFromBottom =
        contentSizeHeight - nativeOffsetRef.current - nativeLayoutHeightRef.current;

      if (distanceFromBottom <= endReachedThreshold) {
        if (!hasFiredNativeEndReached.current) {
          hasFiredNativeEndReached.current = true;
          onEndReached();
        }
      } else if (distanceFromBottom > endReachedThreshold + END_REACHED_RESET_MARGIN) {
        hasFiredNativeEndReached.current = false;
      }
    };

    const handleNativeScroll = (event: NativeSyntheticEvent<NativeScrollEvent>) => {
      const { contentOffset, contentSize, layoutMeasurement } = event.nativeEvent;
      nativeOffsetRef.current = contentOffset.y;
      nativeLayoutHeightRef.current = layoutMeasurement.height;
      evaluateNativeEndReached(contentSize.height);
      onScrollOffsetChange?.(contentOffset.y);
    };

    return (
      <ScrollView
        contentContainerStyle={contentContainerStyle}
        onContentSizeChange={(_width, height) => evaluateNativeEndReached(height)}
        onLayout={(event) => {
          nativeLayoutHeightRef.current = event.nativeEvent.layout.height;
        }}
        onScroll={handleNativeScroll}
        ref={nativeScrollRef}
        refreshControl={
          onRefresh ? (
            <RefreshControl
              colors={refreshIndicatorColor ? [refreshIndicatorColor] : undefined}
              onRefresh={onRefresh}
              refreshing={refreshing}
              tintColor={refreshIndicatorColor}
            />
          ) : undefined
        }
        scrollEventThrottle={16}
        style={[styles.container, style]}>
        {children}
      </ScrollView>
    );
  }

  return (
    <GestureDetector gesture={panGesture}>
      <Animated.View
        onLayout={(event) => setContainerHeight(event.nativeEvent.layout.height)}
        style={[styles.container, style]}>
        {refreshBackgroundColor ? (
          <Animated.View
            pointerEvents="none"
            style={[
              styles.refreshBackground,
              { backgroundColor: refreshBackgroundColor },
            ]}
          />
        ) : null}
        {onRefresh ? (
          <Animated.View
            pointerEvents="none"
            style={[styles.refreshIndicator, refreshIndicatorStyle]}>
            <ActivityIndicator color={refreshIndicatorColor} size="small" />
          </Animated.View>
        ) : null}
        <Animated.View
          onLayout={(event) => setContentHeight(event.nativeEvent.layout.height)}
          style={[contentContainerStyle, animatedContentStyle]}>
          {children}
        </Animated.View>
      </Animated.View>
    </GestureDetector>
  );
});

const styles = StyleSheet.create({
  container: {
    flex: 1,
    overflow: 'hidden',
  },
  refreshIndicator: {
    alignItems: 'center',
    height: REFRESH_HOLD_OFFSET,
    justifyContent: 'center',
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  refreshBackground: {
    height: REFRESH_HOLD_OFFSET * 4,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
});
