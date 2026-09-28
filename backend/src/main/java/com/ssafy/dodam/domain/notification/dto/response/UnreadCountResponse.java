package com.ssafy.dodam.domain.notification.dto.response;

/** GET /api/v1/notifications/unread-count. 삭제되지 않은 알림 중 읽지 않은 수. */
public record UnreadCountResponse(long unreadCount) {
}
