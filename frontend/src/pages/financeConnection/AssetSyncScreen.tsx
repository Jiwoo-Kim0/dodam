import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { Animated, Easing, Image, Text, View } from 'react-native';

import { routes } from '@/constants/routes';
import {
  SYNC_INSTITUTIONS,
  SYNC_PROGRESS_BY_ACTIVE_INDEX,
} from '@/mocks/financeConnection';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';

import { styles } from './AssetSyncScreen.styles';

const STEP_DURATION_MS = 900;
const COMPLETION_DELAY_MS = 700;

export function AssetSyncScreen() {
  const router = useRouter();
  const [activeIndex, setActiveIndex] = useState(1);
  const [displayProgress, setDisplayProgress] = useState(0);
  const [animatedProgress] = useState(() => new Animated.Value(0));
  const isComplete = activeIndex >= SYNC_INSTITUTIONS.length;
  const progress = SYNC_PROGRESS_BY_ACTIVE_INDEX[activeIndex];

  useEffect(() => {
    const listenerId = animatedProgress.addListener(({ value }) => {
      setDisplayProgress(Math.round(value));
    });

    return () => animatedProgress.removeListener(listenerId);
  }, [animatedProgress]);

  useEffect(() => {
    Animated.timing(animatedProgress, {
      toValue: progress,
      duration: STEP_DURATION_MS,
      easing: Easing.out(Easing.ease),
      useNativeDriver: false,
    }).start();
  }, [animatedProgress, progress]);

  useEffect(() => {
    const timer = setTimeout(
      () => {
        if (isComplete) {
          router.replace(routes.notificationPermission);
          return;
        }

        setActiveIndex((index) => index + 1);
      },
      isComplete ? COMPLETION_DELAY_MS : STEP_DURATION_MS,
    );

    return () => clearTimeout(timer);
  }, [activeIndex, isComplete, router]);

  const progressWidth = animatedProgress.interpolate({
    inputRange: [0, 100],
    outputRange: ['0%', '100%'],
    extrapolate: 'clamp',
  });

  return (
    <SafeAreaLayout edges={['left', 'right', 'bottom']} style={styles.container}>
      <Text style={styles.title}>
        {isComplete ? '금융 정보를 모두 모았어요' : '데이터를 모으는 중이에요'}
      </Text>
      <Text style={styles.progressText}>{displayProgress}%</Text>

      <View
        accessibilityRole="progressbar"
        accessibilityValue={{ min: 0, max: 100, now: progress }}
        style={styles.progressTrack}>
        <Animated.View style={[styles.progressFill, { width: progressWidth }]} />
      </View>

      <View style={styles.bankList}>
        {SYNC_INSTITUTIONS.map((bank, index) => {
          const isDone = index < activeIndex || isComplete;
          const isSyncing = index === activeIndex && !isComplete;
          const statusLabel = isDone ? '완료' : isSyncing ? '가져오는 중' : '대기';

          return (
            <View key={bank.id} style={styles.bankRow}>
              <Image source={bank.logo} style={styles.bankLogo} />
              <Text style={styles.bankName}>{bank.name}</Text>
              <Text
                style={[
                  styles.bankStatus,
                  isDone && styles.doneStatus,
                  isSyncing && styles.syncingStatus,
                ]}>
                {statusLabel}
              </Text>
            </View>
          );
        })}
      </View>

      <Text style={styles.notice}>
        최대 1분까지 소요될 수 있어요.{`\n`}앱을 닫아도 계속 진행돼요.
      </Text>
    </SafeAreaLayout>
  );
}
