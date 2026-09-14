package com.ssafy.dodam.domain.notification.controller;

import com.ssafy.dodam.domain.notification.event.NotificationEventService;
import com.ssafy.dodam.domain.notification.event.NotificationEventService.EventRunResult;
import com.ssafy.dodam.domain.notification.service.NotificationClock;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 본인 알림 이벤트 규칙을 지금 실행한다(S15P21D105-581, 명세 22 외 추가). 스케줄(매일 08:00)을 기다리지 않고 시연·확인할 때 쓴다.
 * 본인 데이터만 대상이고 dedupe로 멱등이라 여러 번 눌러도 같은 알림이 늘어나지 않는다.
 */
@RestController
@RequestMapping("/api/v1/notifications/events")
public class NotificationEventController {

    private final NotificationEventService events;
    private final NotificationClock clock;
    private final AuthenticatedUserIdResolver users;

    public NotificationEventController(NotificationEventService events, NotificationClock clock, AuthenticatedUserIdResolver users) {
        this.events = events;
        this.clock = clock;
        this.users = users;
    }

    @PostMapping("/run")
    public ApiResponse<EventRunResult> run(Authentication authentication) {
        return ApiResponse.success(events.runAll(users.resolve(authentication), clock.now()));
    }
}
