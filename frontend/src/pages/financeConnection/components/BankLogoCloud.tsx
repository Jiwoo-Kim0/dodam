import { Image } from 'expo-image';
import { useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { Animated, Easing, Pressable, View } from 'react-native';

import {
  FINANCIAL_INSTITUTIONS,
  type FinancialInstitution,
} from '@/mocks/financeConnection';

import { styles } from './BankLogoCloud.styles';

/**
 * 로고를 흩어 둔 자리. `left`·`top`은 흩뿌림 영역 대비 비율이고,
 * `distance`·`delay`는 화면에 들어올 때 떠오르는 리듬을 로고마다 어긋나게 하려고 다르게 뒀다.
 * 순서는 FINANCIAL_INSTITUTIONS와 같다.
 */
const FLOATING_SPOTS = [
  { left: '6%', top: '77%', size: 56, distance: 9, delay: 460 },
  { left: '54%', top: '0%', size: 46, distance: 7, delay: 120 },
  { left: '58%', top: '78%', size: 58, distance: 8, delay: 520 },
  { left: '70%', top: '33%', size: 48, distance: 6, delay: 300 },
  { left: '10%', top: '40%', size: 60, distance: 9, delay: 240 },
  { left: '40%', top: '32%', size: 52, distance: 7, delay: 180 },
  { left: '80%', top: '64%', size: 48, distance: 8, delay: 420 },
  { left: '16%', top: '64%', size: 62, distance: 10, delay: 380 },
  { left: '44%', top: '56%', size: 52, distance: 7, delay: 340 },
  { left: '78%', top: '6%', size: 62, distance: 9, delay: 200 },
  { left: '30%', top: '0%', size: 50, distance: 6, delay: 60 },
  { left: '2%', top: '9%', size: 60, distance: 8, delay: 0 },
] as const;

type FloatingSpot = (typeof FLOATING_SPOTS)[number];

/** 떠오른 뒤 잠깐 흔들리는 시간. 화면에 들어올 때 한 번만 재생된다. */
const APPEAR_DURATION_MS = 420;
const BOB_DURATION_MS = 700;

interface FloatingBankLogoProps {
  bank: FinancialInstitution;
  spot: FloatingSpot;
}

function FloatingBankLogo({ bank, spot }: FloatingBankLogoProps) {
  const [appearValue] = useState(() => new Animated.Value(0));
  const [bobValue] = useState(() => new Animated.Value(0));
  const [pressValue] = useState(() => new Animated.Value(0));

  useFocusEffect(
    useCallback(() => {
      const bob = (toValue: number, duration: number) =>
        Animated.timing(bobValue, {
          toValue,
          duration,
          easing: Easing.inOut(Easing.sin),
          useNativeDriver: true,
        });

      appearValue.setValue(0);
      bobValue.setValue(0);

      const animation = Animated.sequence([
        Animated.delay(spot.delay),
        Animated.timing(appearValue, {
          toValue: 1,
          duration: APPEAR_DURATION_MS,
          easing: Easing.out(Easing.cubic),
          useNativeDriver: true,
        }),
        // 두 번 정도 잦아들며 흔들리고 멈춘다.
        bob(1, BOB_DURATION_MS),
        bob(0, BOB_DURATION_MS),
        bob(0.45, BOB_DURATION_MS * 0.85),
        bob(0, BOB_DURATION_MS * 0.85),
      ]);

      animation.start();

      return () => animation.stop();
    }, [appearValue, bobValue, spot.delay]),
  );

  const appearTranslateY = appearValue.interpolate({
    inputRange: [0, 1],
    outputRange: [spot.distance + 16, 0],
  });
  const bobTranslateY = bobValue.interpolate({
    inputRange: [0, 1],
    outputRange: [0, -spot.distance],
  });
  const scale = pressValue.interpolate({
    inputRange: [0, 1],
    outputRange: [1, 1.18],
  });

  // 눌렀을 때 한 번 통통 튀는 반응. 화면을 넘기지는 않는다.
  const handlePress = () => {
    Animated.sequence([
      Animated.spring(pressValue, {
        toValue: 1,
        speed: 40,
        bounciness: 16,
        useNativeDriver: true,
      }),
      Animated.spring(pressValue, {
        toValue: 0,
        speed: 18,
        bounciness: 10,
        useNativeDriver: true,
      }),
    ]).start();
  };

  return (
    <Animated.View
      style={[
        styles.floatingSlot,
        {
          left: spot.left,
          top: spot.top,
          opacity: appearValue,
          transform: [
            { translateY: appearTranslateY },
            { translateY: bobTranslateY },
            { scale },
          ],
        },
      ]}>
      <Pressable accessibilityLabel={bank.name} accessibilityRole="image" hitSlop={6} onPress={handlePress}>
        <Image
          contentFit="contain"
          source={bank.logo}
          style={{ width: spot.size, height: spot.size }}
        />
      </Pressable>
    </Animated.View>
  );
}

export function BankLogoCloud() {
  return (
    <View style={styles.container}>
      <View style={styles.scatter}>
        {FINANCIAL_INSTITUTIONS.map((bank, index) => {
          const spot = FLOATING_SPOTS[index];

          return spot ? (
            <FloatingBankLogo key={bank.id} bank={bank} spot={spot} />
          ) : null;
        })}
      </View>

      <Image
        accessibilityLabel="금융 정보를 찾아보는 도리와 토리"
        contentFit="contain"
        source={require('@/assets/characters/dotori/connecting.png')}
        style={styles.character}
      />
    </View>
  );
}
