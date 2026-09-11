package com.ssafy.dodam.domain.deposit.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * 예금 조회·예상금액의 기준일 공급자.
 *
 * <p>535의 {@code DepositSavingClock}과 같은 패턴이다. 서비스가 {@code LocalDate.now()}를 직접 부르면
 * 한 요청 안에서 자정을 넘길 때 예치일수와 응답 기준일이 어긋나므로, 요청마다 한 번만 읽어 내려보낸다.
 * 535의 컴포넌트를 그대로 주입받지 않고 도메인마다 두는 이유는 병렬 작업 중 공용 파일을 건드리지 않기
 * 위해서다.
 */
@Component
public class DepositClock {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private final Clock clock;

    public DepositClock() {
        this(Clock.system(SERVICE_ZONE));
    }

    public DepositClock(Clock clock) {
        this.clock = clock.withZone(SERVICE_ZONE);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
