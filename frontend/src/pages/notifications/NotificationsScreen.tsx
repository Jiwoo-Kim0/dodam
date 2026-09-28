import { useFocusEffect, useRouter } from 'expo-router';
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
import {
  getNotificationDestination,
  type NotificationDestination,
} from '@/utils/notification';

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
    cancel,
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
    reload,
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
  const detailInFlightIdRef = useRef<number | null>(null);
  const deleteConfirmationIdRef = useRef<number | null>(null);
  const deleteInFlightRef = useRef(false);
  const markAllReadInFlightRef = useRef(false);
  const moveInFlightRef = useRef(false);

  useFocusEffect(useCallback(() => {
    void reload();
    return () => {
      cancel();
      detailRequestIdRef.current += 1;
      moveRequestIdRef.current += 1;
      detailInFlightIdRef.current = null;
      deleteConfirmationIdRef.current = null;
      moveInFlightRef.current = false;
      setSelectedId(null);
      setDetail(null);
      setDetailError('');
      setIsDetailLoading(false);
      setIsMoving(false);
      setOpenNotificationId(null);
    };
  }, [cancel, reload]));

  const handleBackPress = () => {
    if (router.canGoBack()) {
      router.back();
      return;
    }

    router.replace(routes.home);
  };

  const loadDetail = useCallback(
    async (notificationId: number) => {
      if (detailInFlightIdRef.current === notificationId) return;

      detailInFlightIdRef.current = notificationId;
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
          detailInFlightIdRef.current = null;
          setIsDetailLoading(false);
        }
      }
    },
    [applyNotification, markRead],
  );

  const openDetail = (notification: NotificationApiResponse) => {
    if (deleteInFlightRef.current) return;
    setSelectedId(notification.notificationId);
    setDetail(notification);
    void loadDetail(notification.notificationId);
  };

  const closeDetail = () => {
    if (deleteInFlightRef.current || moveInFlightRef.current) return;
    moveRequestIdRef.current += 1;
    detailRequestIdRef.current += 1;
    setSelectedId(null);
    setDetail(null);
    setDetailError('');
    detailInFlightIdRef.current = null;
  };

  const handleMarkAllRead = async () => {
    if (markAllReadInFlightRef.current || unreadCount === 0) return;

    markAllReadInFlightRef.current = true;
    setIsMarkingAllRead(true);
    try {
      await markAllRead();
    } catch (markError) {
      Alert.alert(
        '알림을 읽음 처리하지 못했어요',
        getErrorMessage(markError, '네트워크 상태를 확인한 뒤 다시 시도해주세요.'),
      );
    } finally {
      markAllReadInFlightRef.current = false;
      setIsMarkingAllRead(false);
    }
  };

  const confirmDelete = (notificationId: number) => {
    if (
      deleteInFlightRef.current ||
      deleteConfirmationIdRef.current !== null
    ) return;

    deleteConfirmationIdRef.current = notificationId;
    Alert.alert('알림을 삭제할까요?', '삭제한 알림은 다시 확인할 수 없어요.', [
      {
        text: '취소',
        style: 'cancel',
        onPress: () => {
          deleteConfirmationIdRef.current = null;
        },
      },
      {
        text: '삭제',
        style: 'destructive',
        onPress: () => {
          deleteConfirmationIdRef.current = null;
          if (deleteInFlightRef.current) return;
          deleteInFlightRef.current = true;
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
            .finally(() => {
              deleteInFlightRef.current = false;
              setIsDeleting(false);
            });
        },
      },
    ], {
      onDismiss: () => {
        deleteConfirmationIdRef.current = null;
      },
    });
  };

  const handleDelete = () => {
    if (selectedId !== null) confirmDelete(selectedId);
  };

  /**
   * 추천 알림의 sourceId는 추천 ID다(backend/docs/notification.md 원천 연결 규약).
   * 발견 상세는 `진단ID-문제ID`로 열리므로 추천을 한 번 조회해 원천 문제를 찾는다.
   * 문제를 특정할 수 없으면(628 이전 추천·여러 문제를 합친 추천·조회 실패) 발견 목록으로 보낸다.
   */
  const resolveRecommendationRoute = async (recommendationId: string) => {
    const id = Number(recommendationId);
    if (!Number.isSafeInteger(id) || id < 1) return routes.discoveryInsights;

    try {
      const recommendation = await recommendationApi.getRecommendation(id);
      if (recommendation.sourceIssueId === null) return routes.discoveryInsights;

      return routes.discoveryInsightDetail(
        issueRouteId(recommendation.diagnosisId, recommendation.sourceIssueId),
      );
    } catch {
      return routes.discoveryInsights;
    }
  };

  const pushDestination = (
    destination: Exclude<NotificationDestination, { kind: 'recommendation' }>,
  ) => {
    switch (destination.kind) {
      case 'dailyBriefing':
        router.push(routes.dailyBriefing);
        return;
      case 'report':
        router.push(routes.report);
        return;
      case 'goalDetail':
        router.push(routes.goalDetail(destination.goalId));
        return;
      case 'discoveryInsights':
        router.push(routes.discoveryInsights);
        return;
      case 'assets':
      default:
        router.push(routes.assets);
    }
  };

  const moveToDestination = async () => {
    if (!detail || moveInFlightRef.current) return;

    const destination = getNotificationDestination(detail);
    if (!destination) return;

    if (destination.kind === 'recommendation') {
      const requestId = ++moveRequestIdRef.current;
      moveInFlightRef.current = true;
      setIsMoving(true);
      try {
        const route = await resolveRecommendationRoute(destination.recommendationId);
        if (requestId !== moveRequestIdRef.current) return;
        moveInFlightRef.current = false;
        setIsMoving(false);
        closeDetail();
        router.push(route);
      } finally {
        if (requestId === moveRequestIdRef.current) {
          moveInFlightRef.current = false;
          setIsMoving(false);
        }
      }
      return;
    }

    closeDetail();
    pushDestination(destination);
  };

  const subtitle = unreadCount === null
    ? '미확인 알림 수를 확인할 수 없어요'
    : unreadCount > 0 ? `읽지 않은 알림 ${unreadCount}개` : '새 알림이 없어요';

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
              {error ? (
                <View style={styles.inlineError}>
                  <Text accessibilityRole="alert" style={styles.statusText}>
                    다음 알림을 불러오지 못했어요.
                  </Text>
                  <Pressable
                    accessibilityRole="button"
                    onPress={() => void refetch()}
                    style={({ pressed }) => [
                      styles.retryButton,
                      pressed && styles.pressed,
                    ]}>
                    <Text style={styles.retryLabel}>다시 시도</Text>
                  </Pressable>
                </View>
              ) : null}
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
