package com.ssafy.dodam.domain.report.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** 리포트 기준일·생성 시각의 단일 출처. 테스트가 대체한다. 월 경계는 Asia/Seoul이다. */
@Component
public class ReportClock {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    public LocalDateTime now() {
        return LocalDateTime.now(SERVICE_ZONE);
    }

    public LocalDate today() {
        return LocalDate.now(SERVICE_ZONE);
    }
}
