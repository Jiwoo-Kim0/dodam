package com.ssafy.dodam.global.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FutureYearMonthPeriodPolicyTest {
    private final FutureYearMonthPeriodPolicy policy = new FutureYearMonthPeriodPolicy();
    private final YearMonth now = YearMonth.of(2026, 9);

    @Test
    void resolvesDefaultAndPartialRangesAcrossYears() {
        assertThat(policy.resolve(null, null, now))
                .isEqualTo(new YearMonthPeriod(now, YearMonth.of(2027, 2)));
        assertThat(policy.resolve("2026-12", null, now))
                .isEqualTo(new YearMonthPeriod(YearMonth.of(2026, 12), YearMonth.of(2027, 5)));
        assertThat(policy.resolve(null, "2026-11", now))
                .isEqualTo(new YearMonthPeriod(now, YearMonth.of(2026, 11)));
        assertThat(policy.resolve("2026-09", "2027-08", now))
                .isEqualTo(policy.forecast(12, now));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "2026-9", "2026-00", "2026-13", "0000-01", "10000-01", "2026-09-01", " 2026-09"})
    void rejectsInvalidFormats(String value) {
        assertThatThrownBy(() -> policy.resolve(value, null, now))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> policy.resolve(null, value, now))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsPastReversedTooLongAndOverflowingRanges() {
        assertThatThrownBy(() -> policy.resolve("2026-08", "2026-09", now))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> policy.resolve("2026-11", "2026-10", now))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> policy.resolve("2026-09", "2027-09", now))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> policy.resolve("9999-12", null, now))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> policy.forecast(2, YearMonth.of(9999, 12)))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(policy.resolve("9999-12", "9999-12", now).endMonth())
                .isEqualTo(YearMonth.of(9999, 12));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 13, Integer.MAX_VALUE})
    void rejectsInvalidForecastLengths(int value) {
        assertThatThrownBy(() -> policy.forecast(value, now))
                .isInstanceOf(InvalidRequestException.class);
    }
}
