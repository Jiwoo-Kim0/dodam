package com.ssafy.dodam.domain.minusaccount.dto.response;

import com.ssafy.dodam.domain.minusaccount.repository.MinusAccountAlertRow;
import java.time.LocalDateTime;

/**
 * 마이너스통장 알림 한 건. 읽음 처리·삭제는 `22 Notification` API가 소유한다.
 */
public record MinusAccountAlertResponse(
        Long notificationId,
        String type,
        String title,
        String message,
        boolean isRead,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {

    public static MinusAccountAlertResponse from(MinusAccountAlertRow row) {
        return new MinusAccountAlertResponse(
                row.notificationId(),
                row.type(),
                row.title(),
                row.message(),
                row.read(),
                row.createdAt(),
                row.readAt()
        );
    }
}
