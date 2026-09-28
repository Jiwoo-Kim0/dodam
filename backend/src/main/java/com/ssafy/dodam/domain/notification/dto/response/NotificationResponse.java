package com.ssafy.dodam.domain.notification.dto.response;

import com.ssafy.dodam.domain.notification.entity.Notification;
import java.time.LocalDateTime;

/**
 * 알림 1건. 목록·상세·읽음 처리 응답에 같은 형식을 쓴다.
 *
 * @param notificationId 알림 ID
 * @param type           알림 유형 코드({@code NotificationType} 이름. 다른 도메인이 넣은 미지 값도 그대로 나간다)
 * @param title          제목
 * @param message        내용
 * @param sourceType     원천 도메인 코드(MINUS_ACCOUNT, SUBSCRIPTION, GOAL ...). 없으면 null
 * @param sourceId       원천 PK 문자열. FE는 (sourceType, sourceId)로 상세 화면에 이동한다
 * @param isRead         읽음 여부
 * @param createdAt      생성 시각
 * @param readAt         읽은 시각. 읽지 않았으면 null
 */
public record NotificationResponse(
        Long notificationId,
        String type,
        String title,
        String message,
        String sourceType,
        String sourceId,
        boolean isRead,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(), notification.getType(), notification.getTitle(), notification.getMessage(),
                notification.getSourceType(), notification.getSourceId(), notification.isRead(),
                notification.getCreatedAt(), notification.getReadAt());
    }
}
