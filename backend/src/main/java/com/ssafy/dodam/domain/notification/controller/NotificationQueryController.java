package com.ssafy.dodam.domain.notification.controller;

import com.ssafy.dodam.domain.notification.dto.response.NotificationResponse;
import com.ssafy.dodam.domain.notification.dto.response.UnreadCountResponse;
import com.ssafy.dodam.domain.notification.service.NotificationQueryService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.response.PageResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 명세 22. Notification 조회(S15P21D105-519): 목록·읽지 않은 수·상세. 변경은 {@link NotificationCommandController}(520).
 *
 * <p>`type` 값 검증은 서비스({@code NotificationType.parseFilter})가 한다(모르는 값 400 INVALID_REQUEST). 여기서는 형식·길이만 막는다.
 * 날짜는 ISO `yyyy-MM-dd`이며 형식이 틀리면 공통 처리로 400이다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationQueryController {

    static final String CODE_PATTERN = "^\\s*$|^[A-Za-z0-9_]+$";

    private final NotificationQueryService query;
    private final AuthenticatedUserIdResolver users;

    public NotificationQueryController(NotificationQueryService query, AuthenticatedUserIdResolver users) {
        this.query = query;
        this.users = users;
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @RequestParam(required = false) @Size(max = 50) @Pattern(regexp = CODE_PATTERN) String type,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return ApiResponse.success(query.getNotifications(users.resolve(authentication), type, isRead, fromDate, toDate, page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount(Authentication authentication) {
        return ApiResponse.success(query.getUnreadCount(users.resolve(authentication)));
    }

    @GetMapping("/{notificationId}")
    public ApiResponse<NotificationResponse> detail(@PathVariable @Positive Long notificationId, Authentication authentication) {
        return ApiResponse.success(query.getNotification(users.resolve(authentication), notificationId));
    }
}
