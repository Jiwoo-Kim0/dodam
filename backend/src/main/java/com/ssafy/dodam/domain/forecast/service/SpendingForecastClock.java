package com.ssafy.dodam.domain.forecast.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * 예측 기준 시각 공급자(S15P21D105-706). 계좌 도메인의 {@code AccountClock}과 같은 패턴이며 테스트에서 고정 시각을 주입한다.
 *
 * <p>{@code LocalDate.now()}를 직접 부르면 월 경계 테스트를 쓸 수 없다. 예측 창은 "완료된 달"로만 잡으므로 기준 달이 하루만
 * 어긋나도 입력 구간과 예측 구간이 통째로 밀린다.
 */
@Component
public class SpendingForecastClock {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public SpendingForecastClock() {
        this(Clock.system(SERVICE_ZONE));
    }

    public SpendingForecastClock(Clock clock) {
        this.clock = clock.withZone(SERVICE_ZONE);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** 오늘이 속한 달. 이 달은 아직 끝나지 않았으므로 추정 입력에서 빠지고 예측은 다음 달부터 시작한다 */
    public YearMonth currentMonth() {
        return YearMonth.from(today());
    }
}
