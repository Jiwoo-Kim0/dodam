import { useEffect, useState } from 'react';
import { Animated, Easing, View } from 'react-native';

import { styles } from './CoachTypingBubble.styles';

const DOT_COUNT = 3;
/** 점 하나가 떴다 가라앉는 시간. 셋이 이 간격만큼 밀려 시작한다. */
const DOT_DURATION = 420;

/**
 * 응답이 8~15초 걸려서 기다리는 동안 화면이 멈춘 것처럼 보이면 안 된다.
 * 점 세 개를 차례로 띄워 도리가 답을 쓰고 있다는 것만 알린다.
 */
export function CoachTypingBubble() {
  // ref로 들면 렌더 중 접근이라 `react-hooks/refs`에 걸린다. 한 번만 만들고 그대로 쓴다.
  const [dots] = useState(() =>
    Array.from({ length: DOT_COUNT }, () => new Animated.Value(0)),
  );

  useEffect(() => {
    const animations = dots.map((dot, index) =>
      Animated.loop(
        Animated.sequence([
          Animated.delay(index * (DOT_DURATION / DOT_COUNT)),
          Animated.timing(dot, {
            duration: DOT_DURATION,
            easing: Easing.inOut(Easing.quad),
            toValue: 1,
            useNativeDriver: true,
          }),
          Animated.timing(dot, {
            duration: DOT_DURATION,
            easing: Easing.inOut(Easing.quad),
            toValue: 0,
            useNativeDriver: true,
          }),
        ]),
      ),
    );

    animations.forEach((animation) => animation.start());
    return () => animations.forEach((animation) => animation.stop());
  }, [dots]);

  return (
    <View accessibilityLabel="도리가 답변을 준비하고 있어요" accessible style={styles.bubble}>
      {dots.map((dot, index) => (
        <Animated.View
          key={index}
          style={[
            styles.dot,
            {
              opacity: dot.interpolate({ inputRange: [0, 1], outputRange: [0.3, 1] }),
              transform: [
                { translateY: dot.interpolate({ inputRange: [0, 1], outputRange: [0, -4] }) },
              ],
            },
          ]}
        />
      ))}
    </View>
  );
}
