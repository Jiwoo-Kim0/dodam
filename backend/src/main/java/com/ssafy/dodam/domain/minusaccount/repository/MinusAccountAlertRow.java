package com.ssafy.dodam.domain.minusaccount.repository;

import java.time.LocalDateTime;

/**
 * 마이너스통장 알림 한 건의 조회 결과.
 */
public record MinusAccountAlertRow(
        Long notificationId,
        String type,
        String title,
        String message,
        boolean read,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {
}
