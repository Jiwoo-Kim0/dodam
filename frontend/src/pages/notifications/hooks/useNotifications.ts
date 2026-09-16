import { useCallback, useEffect, useRef, useState } from 'react';

import { notificationApi } from '@/api';
import type { NotificationApiResponse } from '@/types/notificationApi.types';

const PAGE_SIZE = 20;

export function useNotifications() {
  const [notifications, setNotifications] = useState<NotificationApiResponse[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [error, setError] = useState<unknown>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const requestIdRef = useRef(0);
  /**
   * 서버 목록에서 이미 가져온 항목 수다. 목록 API가 offset(page × size) 기반이라
   * 알림을 삭제하면 뒤 항목이 한 칸씩 당겨지므로, 다음 페이지 번호를 그대로 쓰면 1건이 건너뛰어진다.
   * 삭제할 때마다 이 값을 줄여서 당겨진 위치부터 다시 읽는다.
   */
  const serverOffsetRef = useRef(0);

  /**
   * 미확인 수 API가 실패하면 목록 API의 `isRead=false` 전체 건수로 대신 센다.
   * 첫 페이지만 세면 알림이 여러 페이지일 때 미확인 수가 실제보다 적게 나온다.
   */
  const loadUnreadCount = useCallback(async () => {
    try {
      const { unreadCount: count } = await notificationApi.getUnreadCount();
      return count;
    } catch {
      const unreadPage = await notificationApi.getNotifications({
        isRead: false,
        page: 0,
        size: 1,
      });
      return unreadPage.totalElements;
    }
  }, []);

  const loadFirstPage = useCallback(
    async (refreshing = false) => {
      const requestId = ++requestIdRef.current;
      setError(null);
      if (refreshing) {
        setIsRefreshing(true);
      } else {
        setIsLoading(true);
      }

      try {
        const page = await notificationApi.getNotifications({ page: 0, size: PAGE_SIZE });
        if (requestId !== requestIdRef.current) return;

        setNotifications(page.content);
        setHasMore(!page.last);
        serverOffsetRef.current = page.content.length;

        try {
          const count = await loadUnreadCount();
          if (requestId === requestIdRef.current) {
            setUnreadCount(count);
          }
        } catch {
          // 서버 집계를 둘 다 얻지 못했다. 첫 페이지가 마지막 페이지일 때만 목록으로 셀 수 있다.
          // 뒤에 페이지가 더 있으면 실제보다 적게 나오므로 직전 값을 그대로 둔다.
          if (requestId === requestIdRef.current && page.last) {
            setUnreadCount(page.content.filter((item) => !item.isRead).length);
          }
        }
      } catch (loadError) {
        if (requestId === requestIdRef.current) {
          setError(loadError);
        }
      } finally {
        if (requestId === requestIdRef.current) {
          setIsLoading(false);
          setIsRefreshing(false);
        }
      }
    },
    [loadUnreadCount],
  );

  useEffect(() => {
    const loadTimeout = setTimeout(() => void loadFirstPage(), 0);

    return () => {
      clearTimeout(loadTimeout);
      requestIdRef.current += 1;
    };
  }, [loadFirstPage]);

  const loadMore = useCallback(async () => {
    if (!hasMore || isLoadingMore || isLoading || isRefreshing) return;

    const offset = serverOffsetRef.current;
    const requestedPage = Math.floor(offset / PAGE_SIZE);
    setIsLoadingMore(true);
    try {
      const page = await notificationApi.getNotifications({
        page: requestedPage,
        size: PAGE_SIZE,
      });
      // 삭제로 offset이 페이지 경계에 걸치면 이미 읽은 앞부분이 다시 온다. 그만큼 잘라낸다.
      const fresh = page.content.slice(offset - requestedPage * PAGE_SIZE);
      setNotifications((current) => {
        const existingIds = new Set(current.map((item) => item.notificationId));
        return [
          ...current,
          ...fresh.filter((item) => !existingIds.has(item.notificationId)),
        ];
      });
      serverOffsetRef.current = requestedPage * PAGE_SIZE + page.content.length;
      setHasMore(!page.last);
    } catch (loadError) {
      setError(loadError);
    } finally {
      setIsLoadingMore(false);
    }
  }, [hasMore, isLoading, isLoadingMore, isRefreshing]);

  /**
   * 서버가 돌려준 알림 1건을 목록에 반영한다.
   * 다른 기기에서 먼저 읽어 목록보다 서버가 앞서 있을 수 있으므로,
   * 읽음으로 바뀐 경우에만 미확인 수를 함께 줄여 목록과 어긋나지 않게 한다.
   */
  const applyNotification = useCallback(
    (updated: NotificationApiResponse) => {
      const previous = notifications.find(
        (item) => item.notificationId === updated.notificationId,
      );
      if (previous && !previous.isRead && updated.isRead) {
        setUnreadCount((count) => Math.max(0, count - 1));
      }
      setNotifications((current) =>
        current.map((item) =>
          item.notificationId === updated.notificationId ? updated : item,
        ),
      );
    },
    [notifications],
  );

  const markRead = useCallback(
    async (notificationId: number) => {
      const updated = await notificationApi.markRead(notificationId);
      applyNotification(updated);
      return updated;
    },
    [applyNotification],
  );

  const markAllRead = useCallback(async () => {
    const result = await notificationApi.markAllRead();
    setNotifications((current) =>
      current.map((item) => ({
        ...item,
        isRead: true,
        readAt: item.readAt ?? new Date().toISOString(),
      })),
    );
    setUnreadCount(result.unreadCount);
  }, []);

  const deleteNotification = useCallback(
    async (notificationId: number) => {
      const deleted = notifications.find((item) => item.notificationId === notificationId);
      await notificationApi.deleteNotification(notificationId);
      setNotifications((current) =>
        current.filter((item) => item.notificationId !== notificationId),
      );
      serverOffsetRef.current = Math.max(0, serverOffsetRef.current - 1);
      if (deleted && !deleted.isRead) {
        setUnreadCount((count) => Math.max(0, count - 1));
      }
    },
    [notifications],
  );

  return {
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
    refetch: () => loadFirstPage(true),
    unreadCount,
  };
}
