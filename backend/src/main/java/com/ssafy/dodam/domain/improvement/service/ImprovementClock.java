package com.ssafy.dodam.domain.improvement.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** 개선 행동 기록·측정 시각의 단일 출처. 테스트가 대체한다. RecommendationClock과 같은 방식. */
@Component
public class ImprovementClock {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    public LocalDateTime now() {
        return LocalDateTime.now(SERVICE_ZONE);
    }

    public LocalDate today() {
        return LocalDate.now(SERVICE_ZONE);
    }
}
