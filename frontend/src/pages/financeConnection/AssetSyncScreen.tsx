import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { Animated, Easing, Image, Pressable, Text, View } from 'react-native';

import { BottomActionButton } from '@/components/bottomActionButton/BottomActionButton';
import { routes } from '@/constants/routes';
import {
  SYNC_INSTITUTIONS,
  SYNC_PROGRESS_BY_ACTIVE_INDEX,
} from '@/mocks/financeConnection';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';

import { styles } from './AssetSyncScreen.styles';
import { useAssetSync } from './useAssetSync';

const STEP_DURATION_MS = 900;
const COMPLETION_DELAY_MS = 700;
/** 서버 응답이 오기 전까지 진행 표시가 멈춰 있을 단계. 마지막 단계는 실제 완료에만 쓴다. */
const LAST_PENDING_INDEX = SYNC_INSTITUTIONS.length - 1;
const COMPLETED_INDEX = SYNC_INSTITUTIONS.length;

export function AssetSyncScreen() {
  const router = useRouter();
  const { phase, failureMessage, requiresReconnect, retry } = useAssetSync();
  const [activeIndex, setActiveIndex] = useState(1);
  const [displayProgress, setDisplayProgress] = useState(0);
  const [animatedProgress] = useState(() => new Animated.Value(0));
  const isComplete = phase === 'completed';
  const isFailed = phase === 'failed';
  const progress = SYNC_PROGRESS_BY_ACTIVE_INDEX[isComplete ? COMPLETED_INDEX : activeIndex];

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

  // 갱신은 한 번의 요청으로 끝나 중간 진행률이 없다. 응답을 기다리는 동안 마지막 단계 앞에서 멈춘다.
  useEffect(() => {
    if (phase !== 'syncing' || activeIndex >= LAST_PENDING_INDEX) return;

    const timer = setTimeout(
      () => setActiveIndex((index) => index + 1),
      STEP_DURATION_MS,
    );

    return () => clearTimeout(timer);
  }, [activeIndex, phase]);

  useEffect(() => {
    if (!isComplete) return;

    const timer = setTimeout(
      () => router.replace(routes.notificationPermission),
      COMPLETION_DELAY_MS,
    );

    return () => clearTimeout(timer);
  }, [isComplete, router]);

  const handleRetry = () => {
    setActiveIndex(1);
    retry();
  };

  const progressWidth = animatedProgress.interpolate({
    inputRange: [0, 100],
    outputRange: ['0%', '100%'],
    extrapolate: 'clamp',
  });

  const title = isFailed
    ? '금융 정보를 가져오지 못했어요'
    : isComplete
      ? '금융 정보를 모두 모았어요'
      : '데이터를 모으는 중이에요';

  return (
    <SafeAreaLayout edges={['left', 'right', 'bottom']} style={styles.container}>
      <Text style={styles.title}>{title}</Text>
      <Text style={styles.progressText}>{displayProgress}%</Text>

      <View
        accessibilityRole="progressbar"
        accessibilityValue={{ min: 0, max: 100, now: progress }}
        style={styles.progressTrack}>
        <Animated.View style={[styles.progressFill, { width: progressWidth }]} />
      </View>

      <View style={styles.bankList}>
        {SYNC_INSTITUTIONS.map((bank, index) => {
          const isCurrent = index === activeIndex;
          const isDone = isComplete || index < activeIndex;
          const isSyncing = isCurrent && phase === 'syncing';
          const isRowFailed = isCurrent && isFailed;
          const statusLabel = isDone
            ? '완료'
            : isSyncing
              ? '가져오는 중'
              : isRowFailed
                ? '실패'
                : '대기';

          return (
            <View key={bank.id} style={styles.bankRow}>
              <Image source={bank.logo} style={styles.bankLogo} />
              <Text style={styles.bankName}>{bank.name}</Text>
              <Text
                style={[
                  styles.bankStatus,
                  isDone && styles.doneStatus,
                  isSyncing && styles.syncingStatus,
                  isRowFailed && styles.failedStatus,
                ]}>
                {statusLabel}
              </Text>
            </View>
          );
        })}
      </View>

      {isFailed ? (
        <View style={styles.errorSection}>
          <Text accessibilityRole="alert" style={styles.errorMessage}>
            {failureMessage}
          </Text>

          <Pressable
            accessibilityLabel="금융 정보 연결을 나중에 하기"
            accessibilityRole="button"
            hitSlop={8}
            onPress={() => router.replace(routes.notificationPermission)}
            style={styles.skipButton}>
            <Text style={styles.skipLabel}>나중에 하기</Text>
          </Pressable>

          {requiresReconnect ? (
            <BottomActionButton
              label="연결 다시 하기"
              onPress={() => router.replace(routes.certificateSelection)}
            />
          ) : (
            <BottomActionButton label="다시 시도" onPress={handleRetry} />
          )}
        </View>
      ) : (
        <Text style={styles.notice}>
          최대 1분까지 소요될 수 있어요.{`\n`}앱을 닫아도 계속 진행돼요.
        </Text>
      )}
    </SafeAreaLayout>
  );
}
