package com.ssafy.dodam.domain.goal.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * 목표 생성·수정 시각과 기준일 공급자. 다른 도메인의 Clock 컴포넌트와 같은 패턴으로 테스트에서 고정 시각을 주입할 수 있다.
 */
@Component
public class GoalClock {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public GoalClock() {
        this(Clock.system(SERVICE_ZONE));
    }

    public GoalClock(Clock clock) {
        this.clock = clock.withZone(SERVICE_ZONE);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
