package com.ssafy.dodam.domain.notification.service;

import com.ssafy.dodam.domain.notification.dto.response.NotificationResponse;
import com.ssafy.dodam.domain.notification.dto.response.UnreadCountResponse;
import com.ssafy.dodam.domain.notification.entity.Notification;
import com.ssafy.dodam.domain.notification.entity.NotificationType;
import com.ssafy.dodam.domain.notification.repository.NotificationRepository;
import com.ssafy.dodam.domain.notification.repository.NotificationSpecifications;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.response.PageResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 22 알림 목록·상세·읽지 않은 수(S15P21D105-519).
 *
 * <p>모든 조회는 본인 소유·미삭제 알림만 본다. 타인 것·없는 것·삭제된 것은 구분하지 않고 404다.
 * 목록은 생성 시각 내림차순(같으면 ID 내림차순)이고 필터는 유형·읽음 여부·생성일 범위다. 날짜 필터는 `created_at`의 날짜 기준으로
 * 시작일·종료일을 모두 포함한다. 변경은 {@link NotificationCommandService}(520).
 */
@Service
@Transactional(readOnly = true)
public class NotificationQueryService {

    static final String NOT_FOUND_MESSAGE = "알림을 찾을 수 없습니다.";
    private static final Sort LIST_ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final NotificationRepository notifications;

    public NotificationQueryService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    public PageResponse<NotificationResponse> getNotifications(
            Long userId, String type, Boolean isRead, LocalDate fromDate, LocalDate toDate, int page, int size
    ) {
        NotificationType typeFilter = NotificationType.parseFilter(type);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new InvalidRequestException("시작일이 종료일보다 늦을 수 없습니다.");
        }
        LocalDateTime from = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime to = toDate == null ? null : toDate.plusDays(1).atStartOfDay();
        return PageResponse.from(
                notifications.findAll(NotificationSpecifications.of(userId, typeFilter, isRead, from, to),
                        PageRequest.of(page, size, LIST_ORDER)),
                NotificationResponse::from);
    }

    public NotificationResponse getNotification(Long userId, Long notificationId) {
        return NotificationResponse.from(owned(notifications, userId, notificationId));
    }

    public UnreadCountResponse getUnreadCount(Long userId) {
        return new UnreadCountResponse(notifications.countByUserIdAndReadFalseAndDeletedAtIsNull(userId));
    }

    /** 본인 소유·미삭제 알림. 조회·변경 서비스가 같은 규칙을 쓴다. */
    static Notification owned(NotificationRepository notifications, Long userId, Long notificationId) {
        return notifications.findByIdAndUserIdAndDeletedAtIsNull(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }
}
