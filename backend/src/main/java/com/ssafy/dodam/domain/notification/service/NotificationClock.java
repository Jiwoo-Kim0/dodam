package com.ssafy.dodam.domain.notification.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** 알림 생성·읽음·삭제 시각 공급자. 다른 도메인의 *Clock과 같은 패턴으로 테스트에서 고정 시각을 주입한다. */
@Component
public class NotificationClock {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public NotificationClock() {
        this(Clock.system(SERVICE_ZONE));
    }

    public NotificationClock(Clock clock) {
        this.clock = clock.withZone(SERVICE_ZONE);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
