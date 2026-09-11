package com.ssafy.dodam.domain.transaction.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * 거래 수정 시각 공급자. 예·적금 도메인의 {@code DepositClock}과 같은 패턴으로, 테스트에서 고정 시각을 주입할 수 있게 한다.
 * 도메인마다 따로 두는 이유도 같다(병렬 작업 중 공용 파일을 건드리지 않기 위해).
 */
@Component
public class TransactionClock {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public TransactionClock() {
        this(Clock.system(SERVICE_ZONE));
    }

    public TransactionClock(Clock clock) {
        this.clock = clock.withZone(SERVICE_ZONE);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
