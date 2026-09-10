package com.ssafy.dodam.domain.savings.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class SavingsClock {
    private final Clock clock;

    public SavingsClock() {
        this(Clock.system(ZoneId.of("Asia/Seoul")));
    }

    public SavingsClock(Clock clock) {
        this.clock = clock.withZone(ZoneId.of("Asia/Seoul"));
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}

