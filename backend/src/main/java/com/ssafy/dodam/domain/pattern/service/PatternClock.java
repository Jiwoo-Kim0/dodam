package com.ssafy.dodam.domain.pattern.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * 패턴 분석 기준 시각 공급자. GoalClock·SubscriptionClock과 같은 패턴으로 테스트에서 고정 시각을 주입한다.
 */
@Component
public class PatternClock {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public PatternClock() {
        this(Clock.system(SERVICE_ZONE));
    }

    public PatternClock(Clock clock) {
        this.clock = clock.withZone(SERVICE_ZONE);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
