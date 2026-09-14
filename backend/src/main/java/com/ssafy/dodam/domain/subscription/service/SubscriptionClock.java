package com.ssafy.dodam.domain.subscription.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * 정기결제 변경 시각과 기준일 공급자. GoalClock과 같은 패턴으로 테스트에서 고정 시각을 주입할 수 있다.
 */
@Component
public class SubscriptionClock {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public SubscriptionClock() {
        this(Clock.system(SERVICE_ZONE));
    }

    public SubscriptionClock(Clock clock) {
        this.clock = clock.withZone(SERVICE_ZONE);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
