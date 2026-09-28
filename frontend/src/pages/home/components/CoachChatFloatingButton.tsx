import { Image } from 'expo-image';
import { useEffect, useState } from 'react';
import { Animated, Easing, Pressable } from 'react-native';

import { styles } from './CoachChatFloatingButton.styles';

/** 한 번 오르내리는 데 걸리는 시간. 시선을 뺏지 않을 만큼 느리게 둔다. */
const FLOAT_DURATION = 2600;
/** 위아래로 움직이는 폭(px). */
const FLOAT_DISTANCE = 3;

type CoachChatFloatingButtonProps = {
  onPress: () => void;
};

/**
 * 홈 어디를 보고 있든 토리에게 물어볼 수 있게 화면에 띄워 두는 버튼이다.
 * 배경 없이 캐릭터만 두고, 살짝 떠 있는 움직임으로 누를 수 있다는 걸 알린다.
 */
export function CoachChatFloatingButton({ onPress }: CoachChatFloatingButtonProps) {
  // 렌더 중에 ref를 읽지 않도록 최초 한 번만 만들어 상태로 들고 있는다.
  const [floatValue] = useState(() => new Animated.Value(0));

  useEffect(() => {
    const animation = Animated.loop(
      Animated.sequence([
        Animated.timing(floatValue, {
          duration: FLOAT_DURATION / 2,
          easing: Easing.inOut(Easing.sin),
          toValue: 1,
          useNativeDriver: true,
        }),
        Animated.timing(floatValue, {
          duration: FLOAT_DURATION / 2,
          easing: Easing.inOut(Easing.sin),
          toValue: 0,
          useNativeDriver: true,
        }),
      ]),
    );

    animation.start();

    return () => animation.stop();
  }, [floatValue]);

  const translateY = floatValue.interpolate({
    inputRange: [0, 1],
    outputRange: [0, -FLOAT_DISTANCE],
  });

  return (
    <Animated.View style={[styles.floating, { transform: [{ translateY }] }]}>
      <Pressable
        accessibilityHint="토리에게 묻기 화면을 열어요"
        accessibilityLabel="토리에게 묻기"
        accessibilityRole="button"
        /* 배경이 없어 실제로 눌리는 면이 캐릭터뿐이라 여유를 둔다. */
        hitSlop={10}
        onPress={onPress}
        style={({ pressed }) => [styles.button, pressed && styles.pressed]}>
        <Image
          contentFit="contain"
          source={require('@/assets/characters/tori/tori_chat.png')}
          style={styles.icon}
        />
      </Pressable>
    </Animated.View>
  );
}
