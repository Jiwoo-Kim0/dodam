import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Pressable, Text, View } from 'react-native';

import { authApi, financeDataApi } from '@/api';
import {
  BouncyScrollView,
  type BouncyScrollViewHandle,
} from '@/components/bouncyScrollView/BouncyScrollView';
import { subscribeHomeTabReselect } from '@/components/bottomNavigation/homeTabReselectEvents';
import { routes } from '@/constants/routes';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useUnreadNotificationCount } from '@/hooks/useUnreadNotificationCount';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { colors } from '@/styles';
import { getFinanceErrorMessage } from '@/utils/financeErrorMessage';

import { AssetSummaryCard } from './components/AssetSummaryCard';
import { BriefingCard } from './components/BriefingCard';
import { CoachChatFloatingButton } from './components/CoachChatFloatingButton';
import { ExpenseSummaryCard } from './components/ExpenseSummaryCard';
import { GoalCarousel } from './components/GoalCarousel';
import { HomeHeader } from './components/HomeHeader';
import { HomeMenuBottomSheet } from './components/HomeMenuBottomSheet';
import { SpendingTopList } from './components/SpendingTopList';
import { styles } from './HomeScreen.styles';
import { useHomeSummary } from './hooks/useHomeSummary';

export function HomeScreen() {
  const router = useRouter();
  const scrollRef = useRef<BouncyScrollViewHandle>(null);
  const hasFocusedRef = useRef(false);
  const [sectionSize, setSectionSize] = useState({ width: 0, height: 0 });
  const { user, reload: reloadUser } = useCurrentUser();
  const unreadNotificationCount = useUnreadNotificationCount();
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [isDisconnecting, setIsDisconnecting] = useState(false);
  const [isMenuVisible, setIsMenuVisible] = useState(false);
  const {
    assetOverview,
    error: summaryError,
    isLoading: isSummaryLoading,
    isRefreshing,
    monthLabel,
    monthlyExpense,
    goals,
    briefing,
    topSpending,
    refetch: refetchSummary,
    sectionErrors,
  } = useHomeSummary();

  const handleHomeRefresh = useCallback(() => {
    scrollRef.current?.scrollToTop();
    void refetchSummary();
  }, [refetchSummary]);

  useEffect(() => subscribeHomeTabReselect(handleHomeRefresh), [handleHomeRefresh]);

  useFocusEffect(
    useCallback(() => {
      if (hasFocusedRef.current) {
        void refetchSummary();
      } else {
        hasFocusedRef.current = true;
      }
    }, [refetchSummary]),
  );

  const handleLogout = async () => {
    if (isLoggingOut) return;

    setIsLoggingOut(true);
    try {
      await authApi.logout();
    } catch {
      // The local session is cleared by authApi even if the server is unavailable.
    } finally {
      router.replace(routes.login);
      setIsLoggingOut(false);
    }
  };

  const handleDisconnect = async () => {
    if (isDisconnecting) return;

    setIsDisconnecting(true);
    try {
      await financeDataApi.disconnect();
      reloadUser();
      Alert.alert(
        '금융 연결을 해제했어요',
        '다시 연결하면 금융 정보를 새로 가져올 수 있어요.',
      );
    } catch (error) {
      Alert.alert('연결을 해제하지 못했어요', getFinanceErrorMessage(error));
    } finally {
      setIsDisconnecting(false);
    }
  };

  const confirmDisconnect = () => {
    Alert.alert(
      '금융 연결을 해제할까요?',
      '해제하면 새로운 금융 정보를 가져오지 않아요.',
      [
        { text: '취소', style: 'cancel' },
        {
          text: '연결 해제',
          style: 'destructive',
          onPress: () => void handleDisconnect(),
        },
      ],
    );
  };

  return (
    <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
      <HomeHeader
        onLogoPress={handleHomeRefresh}
        onMenuPress={() => setIsMenuVisible(true)}
        onNotificationPress={() => router.push(routes.notifications)}
        unreadNotificationCount={unreadNotificationCount}
      />

      <BouncyScrollView
        contentContainerStyle={styles.scrollContent}
        onRefresh={handleHomeRefresh}
        ref={scrollRef}
        refreshBackgroundColor={colors.white}
        refreshIndicatorColor={colors.primary.green}
        refreshing={isRefreshing}
        style={styles.scroll}>
        <View
          onLayout={(event) =>
            setSectionSize({
              width: event.nativeEvent.layout.width,
              height: event.nativeEvent.layout.height,
            })
          }
          style={styles.goalSection}>
          <LinearGradient
            // 상단의 흰 배경을 유지한 뒤 목표 영역에서 연두색으로 자연스럽게 전환한다.
            colors={[
              colors.white,
              colors.white,
              colors.home.goalGradientEnd,
            ]}
            locations={[0, 0.32, 1]}
            style={styles.goalSectionBackground}
          />
          {sectionSize.height > 0 ? (
            <Image
              contentFit="contain"
              contentPosition={{ bottom: 0, right: 0 }}
              source={require('@/assets/images/home/goal_section_background.png')}
              style={[styles.treeIllustration, sectionSize]}
            />
          ) : null}

          <View style={styles.greeting}>
            <Text style={styles.greetingText}>
              {user ? (
                <>
                  <Text style={styles.greetingName}>{`${user.name}님`}</Text>
                  {', 좋은 아침이에요'}
                </>
              ) : (
                '좋은 아침이에요'
              )}
            </Text>
            <Text style={styles.greetingHeadline}>오늘도 목표를{'\n'}향해 한 걸음 더</Text>
          </View>

          <GoalCarousel
            key={goals.map((goal) => goal.id).join(':')}
            goals={goals}
            hasError={sectionErrors.goals}
            isLoading={isSummaryLoading}
          />
        </View>

        <View style={styles.body}>
          {summaryError && !isSummaryLoading ? (
            <Pressable
              accessibilityRole="button"
              accessibilityState={{ disabled: isRefreshing }}
              disabled={isRefreshing}
              onPress={() => void refetchSummary()}
              style={({ pressed }) => [
                styles.retryBanner,
                pressed && !isRefreshing && styles.retryBannerPressed,
              ]}>
              <Text style={styles.retryMessage}>일부 정보를 불러오지 못했어요.</Text>
              <Text style={styles.retryAction}>
                {isRefreshing ? '다시 불러오는 중...' : '다시 시도'}
              </Text>
            </Pressable>
          ) : null}
          <ExpenseSummaryCard
            hasError={sectionErrors.monthlyExpense}
            isLoading={isSummaryLoading}
            monthLabel={monthLabel}
            monthlyExpense={monthlyExpense}
            onDetailPress={() => router.push(routes.ledger)}
          />
          <BriefingCard
            briefing={briefing}
            hasError={sectionErrors.briefing}
            isLoading={isSummaryLoading}
            onDetailPress={() => router.push(routes.dailyBriefing)}
          />
          <AssetSummaryCard
            hasError={sectionErrors.assetOverview}
            isLoading={isSummaryLoading}
            onDetailPress={() => router.push(routes.assets)}
            overview={assetOverview}
          />
          <SpendingTopList
            hasError={sectionErrors.topSpending}
            isLoading={isSummaryLoading}
            items={topSpending}
            monthLabel={monthLabel}
          />
        </View>
      </BouncyScrollView>

      {/* 스크롤뷰 뒤에 두어야 목록 위에 뜬다. */}
      <CoachChatFloatingButton onPress={() => router.push(routes.coachChat)} />

      <HomeMenuBottomSheet
        isConnected={user?.financeConnectionStatus === 'CONNECTED'}
        isProcessing={isDisconnecting || isLoggingOut}
        onClose={() => setIsMenuVisible(false)}
        onFinancePress={() => {
          setIsMenuVisible(false);
          if (user?.financeConnectionStatus === 'CONNECTED') {
            confirmDisconnect();
            return;
          }
          router.push(routes.financeConnection);
        }}
        onLogoutPress={() => {
          setIsMenuVisible(false);
          void handleLogout();
        }}
        onSubscriptionsPress={() => {
          setIsMenuVisible(false);
          router.push(routes.subscriptions);
        }}
        visible={isMenuVisible}
      />
    </SafeAreaLayout>
  );
}
