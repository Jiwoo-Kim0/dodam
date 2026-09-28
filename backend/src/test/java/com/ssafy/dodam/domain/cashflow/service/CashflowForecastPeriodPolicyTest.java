package com.ssafy.dodam.domain.cashflow.service;

import static com.ssafy.dodam.domain.cashflow.service.CashflowForecastPeriodPolicy.DEFAULT_HORIZON_DAYS;
import static com.ssafy.dodam.domain.cashflow.service.CashflowForecastPeriodPolicy.MAX_HORIZON_DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.domain.cashflow.dto.CashflowForecastPeriod;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CashflowForecastPeriodPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);

    private final CashflowForecastPeriodPolicy policy = new CashflowForecastPeriodPolicy();

    @Test
    void defaultsToTodayAndDefaultHorizon() {
        CashflowForecastPeriod period = policy.resolve(null, null, TODAY);

        assertThat(period.baseDate()).isEqualTo(TODAY);
        assertThat(period.horizonDays()).isEqualTo(DEFAULT_HORIZON_DAYS);
    }

    @Test
    void periodIncludesBothEndsSoDayCountMatchesHorizon() {
        CashflowForecastPeriod period = policy.resolve(TODAY, 3, TODAY);

        assertThat(period.endDate()).isEqualTo(TODAY.plusDays(2));
    }

    @Test
    void rejectsPastBaseDate() {
        assertThatThrownBy(() -> policy.resolve(TODAY.minusDays(1), 30, TODAY))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void allowsFutureBaseDate() {
        CashflowForecastPeriod period = policy.resolve(TODAY.plusDays(7), 30, TODAY);

        assertThat(period.baseDate()).isEqualTo(TODAY.plusDays(7));
    }

    @Test
    void rejectsHorizonOutsideRange() {
        assertThatThrownBy(() -> policy.resolve(TODAY, 0, TODAY))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> policy.resolve(TODAY, -1, TODAY))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> policy.resolve(TODAY, MAX_HORIZON_DAYS + 1, TODAY))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void allowsHorizonBoundaries() {
        assertThat(policy.resolve(TODAY, 1, TODAY).horizonDays()).isEqualTo(1);
        assertThat(policy.resolve(TODAY, MAX_HORIZON_DAYS, TODAY).horizonDays()).isEqualTo(MAX_HORIZON_DAYS);
    }
}
