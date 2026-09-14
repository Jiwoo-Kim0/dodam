package com.ssafy.dodam.domain.notification.dto.response;

/**
 * PATCH /api/v1/notifications/read-all.
 *
 * @param updatedCount 이번 요청으로 읽음 처리된 건수(이미 읽은 것은 세지 않음)
 * @param unreadCount  처리 후 남은 미확인 수(항상 0)
 */
public record ReadAllResponse(int updatedCount, long unreadCount) {
}
