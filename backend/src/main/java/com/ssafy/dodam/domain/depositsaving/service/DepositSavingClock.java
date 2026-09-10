package com.ssafy.dodam.domain.depositsaving.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class DepositSavingClock {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private final Clock clock;

    public DepositSavingClock() {
        this(Clock.system(SERVICE_ZONE));
    }

    public DepositSavingClock(Clock clock) {
        this.clock = clock.withZone(SERVICE_ZONE);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
