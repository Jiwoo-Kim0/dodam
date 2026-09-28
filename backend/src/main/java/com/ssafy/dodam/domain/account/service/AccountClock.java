package com.ssafy.dodam.domain.account.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * 계좌 적재·갱신 시각 공급자. 예·적금 도메인의 {@code DepositClock}과 같은 패턴으로 테스트에서 고정 시각을 주입할 수 있다.
 */
@Component
public class AccountClock {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public AccountClock() {
        this(Clock.system(SERVICE_ZONE));
    }

    public AccountClock(Clock clock) {
        this.clock = clock.withZone(SERVICE_ZONE);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
