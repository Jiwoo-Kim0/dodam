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
  const [nextPage, setNextPage] = useState<number | null>(null);
  const requestIdRef = useRef(0);

  const loadFirstPage = useCallback(async (refreshing = false) => {
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
      setNextPage(page.last ? null : page.page + 1);

      try {
        const count = await notificationApi.getUnreadCount();
        if (requestId === requestIdRef.current) {
          setUnreadCount(count.unreadCount);
        }
      } catch {
        if (requestId === requestIdRef.current) {
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
  }, []);

  useEffect(() => {
    const loadTimeout = setTimeout(() => void loadFirstPage(), 0);

    return () => {
      clearTimeout(loadTimeout);
      requestIdRef.current += 1;
    };
  }, [loadFirstPage]);

  const loadMore = useCallback(async () => {
    if (nextPage === null || isLoadingMore || isLoading || isRefreshing) return;

    const requestedPage = nextPage;
    setIsLoadingMore(true);
    try {
      const page = await notificationApi.getNotifications({
        page: requestedPage,
        size: PAGE_SIZE,
      });
      setNotifications((current) => {
        const existingIds = new Set(current.map((item) => item.notificationId));
        return [...current, ...page.content.filter((item) => !existingIds.has(item.notificationId))];
      });
      setNextPage(page.last ? null : page.page + 1);
    } catch (loadError) {
      setError(loadError);
    } finally {
      setIsLoadingMore(false);
    }
  }, [isLoading, isLoadingMore, isRefreshing, nextPage]);

  const markRead = useCallback(async (notificationId: number) => {
    const updated = await notificationApi.markRead(notificationId);
    setNotifications((current) =>
      current.map((item) => (item.notificationId === notificationId ? updated : item)),
    );
    setUnreadCount((current) => Math.max(0, current - 1));
    return updated;
  }, []);

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
      if (deleted && !deleted.isRead) {
        setUnreadCount((count) => Math.max(0, count - 1));
      }
    },
    [notifications],
  );

  return {
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
