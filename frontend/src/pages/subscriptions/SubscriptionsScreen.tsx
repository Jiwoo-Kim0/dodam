import { useFocusEffect, useRouter } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { useCallback, useMemo, useState } from 'react';
import { Pressable, Text, View } from 'react-native';

import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { ScreenState } from '@/components/screenState/ScreenState';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { colors } from '@/styles';
import { formatNumber } from '@/utils/currency';

import { SubscriptionListItem } from './components/SubscriptionListItem';
import { useSubscriptions } from './hooks/useSubscriptions';
import { styles } from './SubscriptionsScreen.styles';

export function SubscriptionsScreen() {
  const router = useRouter();
  const { cancel, error, isLoading, isRefreshing, refetch, reload, subscriptions } =
    useSubscriptions();
  const [selectedIds, setSelectedIds] = useState<readonly string[]>([]);

  useFocusEffect(
    useCallback(() => {
      void reload();
      return cancel;
    }, [cancel, reload]),
  );

  const handleToggle = useCallback((subscriptionId: string) => {
    setSelectedIds((previous) =>
      previous.includes(subscriptionId)
        ? previous.filter((id) => id !== subscriptionId)
        : [...previous, subscriptionId],
    );
  }, []);

  // 합계와 건수를 모두 현재 목록에서 센다. 새로고침으로 사라진 구독이 선택에 남아 있어도
  // 화면에 없는 금액이 합계에 섞이지 않는다.
  const selectedTotal = useMemo(
    () =>
      subscriptions.reduce(
        (total, subscription) =>
          selectedIds.includes(subscription.id) ? total + subscription.monthlyAmount : total,
        0,
      ),
    [selectedIds, subscriptions],
  );
  const selectedCount = useMemo(
    () => subscriptions.filter((subscription) => selectedIds.includes(subscription.id)).length,
    [selectedIds, subscriptions],
  );

  const handleBackPress = () => {
    if (router.canGoBack()) {
      router.back();
      return;
    }

    router.replace(routes.home);
  };

  return (
    <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
      <View style={styles.header}>
        <Pressable
          accessibilityLabel="이전 화면으로 이동"
          accessibilityRole="button"
          hitSlop={8}
          onPress={handleBackPress}
          style={({ pressed }) => [styles.backButton, pressed && styles.pressed]}>
          <SymbolView
            name={{ android: 'arrow_back', ios: 'chevron.left', web: 'arrow_back' }}
            size={24}
            tintColor={colors.textPrimary}
          />
        </Pressable>
      </View>

      <RefreshableBouncyScrollView
        contentContainerStyle={styles.scrollContent}
        onRefresh={() => void refetch()}
        refreshing={isRefreshing}>
        {isLoading ? (
          <ScreenState description="구독 목록을 불러오고 있어요" isLoading />
        ) : error ? (
          <ScreenState
            description="네트워크 상태를 확인한 뒤 다시 시도해 주세요."
            onRetry={() => void reload()}
            title="구독 목록을 불러오지 못했어요."
          />
        ) : subscriptions.length === 0 ? (
          <ScreenState
            description="금융 정보를 연결하면 이용 중인 구독을 모아서 보여드려요."
            title="이용 중인 구독이 없어요."
          />
        ) : (
          <>
            <Text style={styles.headline}>
              지금 이용 중인{'\n'}
              구독 {subscriptions.length}건이에요
            </Text>

            {/* 선택이 없을 때도 같은 자리를 지켜, 고를 때마다 아래 목록이 밀리지 않게 한다. */}
            <Text style={styles.summary}>
              {selectedCount === 0
                ? '구독을 선택하면 매달 아낄 수 있는 금액을 알려드려요.'
                : `이 ${selectedCount}개 구독을 해지하면 매달 ${formatNumber(selectedTotal)}원이 남아요.`}
            </Text>

            <View style={styles.list}>
              {subscriptions.map((subscription) => (
                <SubscriptionListItem
                  isSelected={selectedIds.includes(subscription.id)}
                  key={subscription.id}
                  onToggle={handleToggle}
                  subscription={subscription}
                />
              ))}
            </View>
          </>
        )}
      </RefreshableBouncyScrollView>
    </SafeAreaLayout>
  );
}
