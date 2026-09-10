package com.ssafy.dodam.global.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class YearMonthPolicyTest {

    private final YearMonthPolicy policy = new YearMonthPolicy();

    @Test
    void explicitDateControlsDefaultMonthAndFutureValidation() {
        LocalDate date = LocalDate.of(2024, 2, 29);
        assertThat(policy.resolveHistorical(null, date)).isEqualTo(YearMonth.of(2024, 2));
        assertThat(policy.resolveHistorical("2024-01", date)).isEqualTo(YearMonth.of(2024, 1));
        assertThatThrownBy(() -> policy.resolveHistorical("2024-03", date))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void defaultsToCurrentMonthAndAllowsFutureBillingMonth() {
        YearMonth current = YearMonth.now(ZoneId.of("Asia/Seoul"));
        assertThat(policy.resolveHistorical(null)).isEqualTo(current);
        assertThat(policy.resolveBillingMonth(current.plusMonths(1).toString()))
                .isEqualTo(current.plusMonths(1));
    }

    @Test
    void rejectsMalformedAndFutureHistoricalMonth() {
        YearMonth current = YearMonth.now(ZoneId.of("Asia/Seoul"));
        assertThatThrownBy(() -> policy.resolveHistorical("2026-13"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> policy.resolveHistorical(current.plusMonths(1).toString()))
                .isInstanceOf(InvalidRequestException.class);
    }
}
