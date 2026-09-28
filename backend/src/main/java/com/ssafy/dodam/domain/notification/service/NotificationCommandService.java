package com.ssafy.dodam.domain.notification.service;

import com.ssafy.dodam.domain.notification.dto.response.NotificationResponse;
import com.ssafy.dodam.domain.notification.dto.response.ReadAllResponse;
import com.ssafy.dodam.domain.notification.entity.Notification;
import com.ssafy.dodam.domain.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 22 알림 읽음·전체 읽음·삭제(S15P21D105-520).
 *
 * <p>대상은 본인 소유·미삭제 알림만이다(404 규칙은 {@link NotificationQueryService#owned}). 읽음은 멱등, 삭제는 `deleted_at` 소프트 삭제다.
 */
@Service
@Transactional
public class NotificationCommandService {

    private final NotificationRepository notifications;
    private final NotificationClock clock;

    public NotificationCommandService(NotificationRepository notifications, NotificationClock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    /** 이미 읽은 알림은 `readAt`을 바꾸지 않고 그대로 돌려준다(명세 "이미 읽은 알림은 멱등 처리"). */
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = NotificationQueryService.owned(notifications, userId, notificationId);
        notification.markRead(clock.now());
        return NotificationResponse.from(notification);
    }

    /** 읽지 않은 알림 전부를 한 번의 UPDATE로 읽음 처리한다. 타인 알림·삭제된 알림은 건드리지 않는다. */
    public ReadAllResponse markAllRead(Long userId) {
        int updated = notifications.markAllRead(userId, clock.now());
        return new ReadAllResponse(updated, notifications.countByUserIdAndReadFalseAndDeletedAtIsNull(userId));
    }

    /** 소프트 삭제. 이후 목록·상세·미확인 수·전체 읽음 대상에서 빠지고 다시 조회하면 404다. 읽음 상태는 바꾸지 않는다. */
    public void delete(Long userId, Long notificationId) {
        NotificationQueryService.owned(notifications, userId, notificationId).delete(clock.now());
    }
}
