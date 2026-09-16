import { useRouter } from 'expo-router';
import { useCallback, useRef, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, Text, View } from 'react-native';

import { isApiError, notificationApi, recommendationApi } from '@/api';
import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { issueRouteId } from '@/pages/discovery/discoveryModel';
import { colors } from '@/styles';
import type { NotificationApiResponse } from '@/types/notificationApi.types';

import { NotificationCard } from './components/NotificationCard';
import { NotificationDetailSheet } from './components/NotificationDetailSheet';
import { useNotifications } from './hooks/useNotifications';
import { styles } from './NotificationsScreen.styles';

function getErrorMessage(error: unknown, fallback: string) {
  return isApiError(error) ? error.message : fallback;
}

export function NotificationsScreen() {
  const router = useRouter();
  const {
    applyNotification,
    deleteNotification,
    error,
    isLoading,
    isLoadingMore,
    isRefreshing,
    loadMore,
    markAllRead,
    markRead,
    notifications,
    refetch,
    unreadCount,
  } = useNotifications();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<NotificationApiResponse | null>(null);
  const [detailError, setDetailError] = useState('');
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isMarkingAllRead, setIsMarkingAllRead] = useState(false);
  const [openNotificationId, setOpenNotificationId] = useState<number | null>(null);
  const [isMoving, setIsMoving] = useState(false);
  const detailRequestIdRef = useRef(0);
  const moveRequestIdRef = useRef(0);

  const handleBackPress = () => {
    if (router.canGoBack()) {
      router.back();
      return;
    }

    router.replace(routes.home);
  };

  const loadDetail = useCallback(
    async (notificationId: number) => {
      const requestId = ++detailRequestIdRef.current;
      setDetailError('');
      setIsDetailLoading(true);

      try {
        let response = await notificationApi.getNotification(notificationId);
        if (requestId !== detailRequestIdRef.current) return;
        setDetail(response);
        // 다른 기기에서 먼저 읽었을 수 있다. 서버가 준 읽음 상태를 목록에도 반영한다.
        applyNotification(response);

        if (!response.isRead) {
          try {
            response = await markRead(notificationId);
            if (requestId !== detailRequestIdRef.current) return;
            setDetail(response);
          } catch (readError) {
            if (requestId !== detailRequestIdRef.current) return;
            Alert.alert(
              '읽음 처리에 실패했어요',
              getErrorMessage(readError, '네트워크 상태를 확인한 뒤 다시 시도해주세요.'),
            );
          }
        }
      } catch (loadError) {
        if (requestId !== detailRequestIdRef.current) return;
        setDetail(null);
        setDetailError(
          getErrorMessage(loadError, '알림을 불러오지 못했어요. 다시 시도해주세요.'),
        );
      } finally {
        if (requestId === detailRequestIdRef.current) {
          setIsDetailLoading(false);
        }
      }
    },
    [applyNotification, markRead],
  );

  const openDetail = (notification: NotificationApiResponse) => {
    setSelectedId(notification.notificationId);
    setDetail(notification);
    void loadDetail(notification.notificationId);
  };

  const closeDetail = () => {
    if (isDeleting) return;
    moveRequestIdRef.current += 1;
    detailRequestIdRef.current += 1;
    setSelectedId(null);
    setDetail(null);
    setDetailError('');
  };

  const handleMarkAllRead = async () => {
    if (isMarkingAllRead || unreadCount === 0) return;

    setIsMarkingAllRead(true);
    try {
      await markAllRead();
    } catch (markError) {
      Alert.alert(
        '알림을 읽음 처리하지 못했어요',
        getErrorMessage(markError, '네트워크 상태를 확인한 뒤 다시 시도해주세요.'),
      );
    } finally {
      setIsMarkingAllRead(false);
    }
  };

  const confirmDelete = (notificationId: number) => {
    Alert.alert('알림을 삭제할까요?', '삭제한 알림은 다시 확인할 수 없어요.', [
      { text: '취소', style: 'cancel' },
      {
        text: '삭제',
        style: 'destructive',
        onPress: () => {
          setIsDeleting(true);
          void deleteNotification(notificationId)
            .then(() => {
              setOpenNotificationId((current) =>
                current === notificationId ? null : current,
              );
              if (selectedId === notificationId) {
                detailRequestIdRef.current += 1;
                setSelectedId(null);
                setDetail(null);
                setDetailError('');
              }
            })
            .catch((deleteError: unknown) => {
              Alert.alert(
                '알림을 삭제하지 못했어요',
                getErrorMessage(
                  deleteError,
                  '네트워크 상태를 확인한 뒤 다시 시도해주세요.',
                ),
              );
            })
            .finally(() => setIsDeleting(false));
        },
      },
    ]);
  };

  const handleDelete = () => {
    if (selectedId !== null) confirmDelete(selectedId);
  };

  /**
   * 추천 알림의 sourceId는 추천 ID다(backend/docs/notification.md 원천 연결 규약).
   * 발견 상세는 `진단ID-문제ID`로 열리므로 추천을 한 번 조회해 원천 문제를 찾는다.
   * 문제를 특정할 수 없으면(628 이전 추천·여러 문제를 합친 추천·조회 실패) 발견 목록으로 보낸다.
   */
  const resolveRecommendationDestination = async (sourceId: string) => {
    const recommendationId = Number(sourceId);
    if (!Number.isSafeInteger(recommendationId) || recommendationId < 1) {
      return routes.discoveryInsights;
    }

    try {
      const recommendation = await recommendationApi.getRecommendation(recommendationId);
      if (recommendation.sourceIssueId === null) return routes.discoveryInsights;

      return routes.discoveryInsightDetail(
        issueRouteId(recommendation.diagnosisId, recommendation.sourceIssueId),
      );
    } catch {
      return routes.discoveryInsights;
    }
  };

  const moveToDestination = async () => {
    if (!detail || isMoving) return;

    const { sourceId, sourceType, type } = detail;

    if (sourceType === 'RECOMMENDATION' && sourceId) {
      const requestId = ++moveRequestIdRef.current;
      setIsMoving(true);
      const destination = await resolveRecommendationDestination(sourceId);
      if (requestId !== moveRequestIdRef.current) return;
      setIsMoving(false);
      closeDetail();
      router.push(destination);
      return;
    }

    closeDetail();

    if (type === 'DAILY_BRIEFING') {
      router.push(routes.dailyBriefing);
    } else if (type === 'MONTHLY_REPORT' || sourceType === 'REPORT') {
      router.push(routes.report);
    } else if (sourceType === 'GOAL' && sourceId) {
      router.push(routes.goalDetail(sourceId));
    } else if (
      sourceType === 'DIAGNOSIS' ||
      sourceType === 'RECOMMENDATION' ||
      sourceType === 'PATTERN_ANALYSIS' ||
      sourceType === 'SIMULATION'
    ) {
      // 발견 탭 첫 화면은 진단 소개 랜딩이라 알림 목적지로 쓸 수 없다. 실데이터 목록으로 보낸다.
      router.push(routes.discoveryInsights);
    } else if (sourceType === 'CASHFLOW_FORECAST') {
      router.push(routes.report);
    } else {
      router.push(routes.assets);
    }
  };

  const subtitle = unreadCount > 0 ? `읽지 않은 알림 ${unreadCount}개` : '새 알림이 없어요';

  return (
    <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
      <AppHeader
        onBackPress={handleBackPress}
        right={
          <Pressable
            accessibilityLabel="모든 알림 읽음 처리"
            accessibilityRole="button"
            accessibilityState={{ disabled: unreadCount === 0 || isMarkingAllRead }}
            disabled={unreadCount === 0 || isMarkingAllRead}
            hitSlop={8}
            onPress={() => void handleMarkAllRead()}
            style={({ pressed }) => [styles.readAllButton, pressed && styles.pressed]}>
            {isMarkingAllRead ? (
              <ActivityIndicator color={colors.primary.green} size="small" />
            ) : (
              <Text
                style={[
                  styles.readAllLabel,
                  unreadCount === 0 && styles.readAllLabelDisabled,
                ]}>
                모두 읽음
              </Text>
            )}
          </Pressable>
        }
        showBackButton
        subtitle={subtitle}
        title="알림"
      />

      <Pressable
        onPress={() => setOpenNotificationId(null)}
        style={styles.scrollDismissArea}>
        <RefreshableBouncyScrollView
          contentContainerStyle={styles.scrollContent}
          onEndReached={loadMore}
          onRefresh={() => {
            setOpenNotificationId(null);
            void refetch();
          }}
          onScrollOffsetChange={() => setOpenNotificationId(null)}
          refreshing={isRefreshing}>
          {isLoading ? (
            <View style={styles.status}>
              <ActivityIndicator color={colors.primary.green} size="large" />
              <Text style={styles.statusText}>알림을 불러오고 있어요</Text>
            </View>
          ) : error && notifications.length === 0 ? (
            <View style={styles.status}>
              <Text accessibilityRole="alert" style={styles.statusText}>
                알림을 불러오지 못했어요.{`\n`}네트워크 상태를 확인해주세요.
              </Text>
              <Pressable
                accessibilityRole="button"
                onPress={() => void refetch()}
                style={({ pressed }) => [styles.retryButton, pressed && styles.pressed]}>
                <Text style={styles.retryLabel}>다시 시도</Text>
              </Pressable>
            </View>
          ) : notifications.length === 0 ? (
            <View style={styles.status}>
              <Text style={styles.emptyTitle}>도착한 알림이 없어요</Text>
              <Text style={styles.statusText}>
                새로운 금융 소식이 생기면 알려드릴게요.
              </Text>
            </View>
          ) : (
            <View style={styles.list}>
              {notifications.map((notification) => (
                <NotificationCard
                  isOpen={openNotificationId === notification.notificationId}
                  key={notification.notificationId}
                  notification={notification}
                  onClose={() =>
                    setOpenNotificationId((current) =>
                      current === notification.notificationId ? null : current,
                    )
                  }
                  onDelete={() => confirmDelete(notification.notificationId)}
                  onOpen={() => setOpenNotificationId(notification.notificationId)}
                  onPress={() => openDetail(notification)}
                />
              ))}
              {isLoadingMore ? (
                <ActivityIndicator color={colors.primary.green} style={styles.moreIndicator} />
              ) : null}
            </View>
          )}
        </RefreshableBouncyScrollView>
      </Pressable>

      <NotificationDetailSheet
        errorMessage={detailError}
        isDeleting={isDeleting}
        isLoading={isDetailLoading}
        isMoving={isMoving}
        notification={detail}
        onClose={closeDetail}
        onDelete={handleDelete}
        onMoveToDestination={() => void moveToDestination()}
        onRetry={() => {
          if (selectedId !== null) void loadDetail(selectedId);
        }}
        visible={selectedId !== null}
      />
    </SafeAreaLayout>
  );
}
