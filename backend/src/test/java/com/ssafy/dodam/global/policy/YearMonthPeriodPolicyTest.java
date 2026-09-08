package com.ssafy.dodam.global.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class YearMonthPeriodPolicyTest {

    private static final YearMonth CURRENT_MONTH = YearMonth.of(2026, 9);

    private final YearMonthPeriodPolicy policy = new YearMonthPeriodPolicy();

    @Test
    void defaultsToThreeMonthsEndingAtCurrentMonth() {
        YearMonthPeriod period = policy.resolve(null, null, CURRENT_MONTH);

        assertThat(period.startMonth()).isEqualTo(YearMonth.of(2026, 7));
        assertThat(period.endMonth()).isEqualTo(CURRENT_MONTH);
    }

    @Test
    void resolvesOneSidedPeriods() {
        YearMonthPeriod endOnly = policy.resolve(null, "2026-08", CURRENT_MONTH);
        YearMonthPeriod startOnly = policy.resolve("2026-06", null, CURRENT_MONTH);

        assertThat(endOnly.startMonth()).isEqualTo(YearMonth.of(2026, 6));
        assertThat(endOnly.endMonth()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(startOnly.startMonth()).isEqualTo(YearMonth.of(2026, 6));
        assertThat(startOnly.endMonth()).isEqualTo(CURRENT_MONTH);
    }

    @Test
    void acceptsExactlyTwelveIncludedMonths() {
        YearMonthPeriod period = policy.resolve("2025-10", "2026-09", CURRENT_MONTH);

        assertThat(period.startMonth()).isEqualTo(YearMonth.of(2025, 10));
        assertThat(period.endMonth()).isEqualTo(CURRENT_MONTH);
    }

    @Test
    void rejectsInvalidReversedFutureAndTooLongPeriods() {
        assertThatThrownBy(() -> policy.resolve("2026-13", null, CURRENT_MONTH))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> policy.resolve("2026-09", "2026-08", CURRENT_MONTH))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> policy.resolve(null, "2026-10", CURRENT_MONTH))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> policy.resolve("2025-09", "2026-09", CURRENT_MONTH))
                .isInstanceOf(InvalidRequestException.class);
    }
}
