export interface NotificationApiResponse {
  notificationId: number;
  type: string;
  title: string;
  message: string;
  sourceType: string | null;
  sourceId: string | null;
  isRead: boolean;
  createdAt: string;
  readAt: string | null;
}

export interface UnreadNotificationCountApiResponse {
  unreadCount: number;
}

export interface ReadAllNotificationsApiResponse {
  updatedCount: number;
  unreadCount: number;
}
