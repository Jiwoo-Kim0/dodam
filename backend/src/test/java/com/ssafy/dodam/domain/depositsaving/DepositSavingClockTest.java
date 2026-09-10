package com.ssafy.dodam.domain.depositsaving;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.dodam.domain.depositsaving.service.DepositSavingClock;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DepositSavingClockTest {
    @Test
    void seoulMidnightDeterminesDateEvenWhenClockUsesUtc() {
        var clock = new DepositSavingClock(
                Clock.fixed(Instant.parse("2024-02-29T15:00:00Z"), ZoneOffset.UTC));
        assertThat(clock.today()).isEqualTo(LocalDate.of(2024, 3, 1));
    }
}
