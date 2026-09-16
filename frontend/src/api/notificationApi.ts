import type { PageResponse } from '@/api/api.types';
import type {
  NotificationApiResponse,
  ReadAllNotificationsApiResponse,
  UnreadNotificationCountApiResponse,
} from '@/types/notificationApi.types';

import { apiClient } from './apiClient';

interface GetNotificationsParams {
  page?: number;
  size?: number;
}

export const notificationApi = {
  getNotifications: ({ page = 0, size = 20 }: GetNotificationsParams = {}) =>
    apiClient.get<PageResponse<NotificationApiResponse>>('/v1/notifications', {
      query: { page, size },
    }),

  getUnreadCount: () =>
    apiClient.get<UnreadNotificationCountApiResponse>(
      '/v1/notifications/unread-count',
    ),

  getNotification: (notificationId: number) =>
    apiClient.get<NotificationApiResponse>(
      `/v1/notifications/${notificationId}`,
    ),

  markRead: (notificationId: number) =>
    apiClient.patch<NotificationApiResponse>(
      `/v1/notifications/${notificationId}/read`,
    ),

  markAllRead: () =>
    apiClient.patch<ReadAllNotificationsApiResponse>(
      '/v1/notifications/read-all',
    ),

  deleteNotification: (notificationId: number) =>
    apiClient.delete<void>(`/v1/notifications/${notificationId}`),
};
