package com.ssafy.dodam.domain.notification.controller;

import com.ssafy.dodam.domain.notification.dto.response.NotificationResponse;
import com.ssafy.dodam.domain.notification.dto.response.ReadAllResponse;
import com.ssafy.dodam.domain.notification.service.NotificationCommandService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 명세 22. Notification 변경(S15P21D105-520): 전체 읽음·읽음·삭제. 조회는 {@link NotificationQueryController}(519).
 * `/read-all`은 `{notificationId}`보다 먼저 매핑되도록 고정 경로로 두었다(Spring은 리터럴 경로를 우선한다).
 */
@Validated
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationCommandController {

    private final NotificationCommandService commands;
    private final AuthenticatedUserIdResolver users;

    public NotificationCommandController(NotificationCommandService commands, AuthenticatedUserIdResolver users) {
        this.commands = commands;
        this.users = users;
    }

    @PatchMapping("/read-all")
    public ApiResponse<ReadAllResponse> readAll(Authentication authentication) {
        return ApiResponse.success(commands.markAllRead(users.resolve(authentication)));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<NotificationResponse> read(@PathVariable @Positive Long notificationId, Authentication authentication) {
        return ApiResponse.success(commands.markRead(users.resolve(authentication), notificationId));
    }

    @DeleteMapping("/{notificationId}")
    public ApiResponse<Void> delete(@PathVariable @Positive Long notificationId, Authentication authentication) {
        commands.delete(users.resolve(authentication), notificationId);
        return ApiResponse.success(null);
    }
}
