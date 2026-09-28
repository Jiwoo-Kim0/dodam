package com.ssafy.dodam.domain.cashflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.domain.cashflow.dto.CashflowBalanceProjection;
import com.ssafy.dodam.domain.cashflow.dto.CashflowForecastPeriod;
import com.ssafy.dodam.domain.cashflow.entity.CashflowDailyBalance;
import com.ssafy.dodam.domain.cashflow.entity.CashflowEvent;
import com.ssafy.dodam.domain.cashflow.entity.EventDirection;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CashflowDailyBalanceCalculatorTest {

    private static final Long FORECAST_ID = 77L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 9, 10);

    private final CashflowDailyBalanceCalculator calculator = new CashflowDailyBalanceCalculator();

    @Test
    void producesOneRowPerHorizonDay() {
        CashflowBalanceProjection projection = calculate(3, 1_000_000L, List.of());

        assertThat(projection.dailyBalances()).hasSize(3);
        assertThat(projection.dailyBalances()).extracting(CashflowDailyBalance::getBalanceDate)
                .containsExactly(BASE_DATE, BASE_DATE.plusDays(1), BASE_DATE.plusDays(2));
        assertThat(projection.dailyBalances()).extracting(CashflowDailyBalance::getExpectedBalance)
                .containsExactly(1_000_000L, 1_000_000L, 1_000_000L);
        assertThat(projection.firstShortageDate()).isNull();
        assertThat(projection.minimumBalance()).isEqualTo(1_000_000L);
    }

    /** 같은 날 입금을 먼저 반영하지 않으면 급여가 들어오는 날의 카드대금이 잔액 부족으로 잡힌다. */
    @Test
    void appliesInflowBeforeOutflowOnTheSameDay() {
        CashflowBalanceProjection projection = calculate(1, 100_000L, List.of(
                event(BASE_DATE, EventDirection.OUTFLOW, 2_000_000L, "out"),
                event(BASE_DATE, EventDirection.INFLOW, 3_000_000L, "in")));

        assertThat(projection.dailyBalances().get(0).getExpectedBalance()).isEqualTo(1_100_000L);
        assertThat(projection.firstShortageDate()).isNull();
    }

    @Test
    void keepsNegativeBalanceAndReportsFirstShortageDate() {
        CashflowBalanceProjection projection = calculate(3, 100_000L, List.of(
                event(BASE_DATE.plusDays(1), EventDirection.OUTFLOW, 300_000L, "card"),
                event(BASE_DATE.plusDays(2), EventDirection.OUTFLOW, 100_000L, "loan")));

        assertThat(projection.dailyBalances()).extracting(CashflowDailyBalance::getExpectedBalance)
                .containsExactly(100_000L, -200_000L, -300_000L);
        assertThat(projection.firstShortageDate()).isEqualTo(BASE_DATE.plusDays(1));
        assertThat(projection.minimumBalance()).isEqualTo(-300_000L);
    }

    /** 최소잔액은 저장되는 일별 잔액 중에서만 고른다. 시작 잔액은 어느 날짜의 잔액도 아니다. */
    @Test
    void minimumBalanceIgnoresStartingBalance() {
        CashflowBalanceProjection projection = calculate(2, 100_000L, List.of(
                event(BASE_DATE, EventDirection.INFLOW, 500_000L, "salary")));

        assertThat(projection.minimumBalance()).isEqualTo(600_000L);
    }

    @Test
    void ignoresEventsOutsideThePeriod() {
        CashflowBalanceProjection projection = calculate(2, 100_000L, List.of(
                event(BASE_DATE.minusDays(1), EventDirection.OUTFLOW, 90_000L, "before"),
                event(BASE_DATE.plusDays(5), EventDirection.OUTFLOW, 90_000L, "after")));

        assertThat(projection.dailyBalances()).extracting(CashflowDailyBalance::getExpectedBalance)
                .containsExactly(100_000L, 100_000L);
    }

    @Test
    void failsLoudlyOnOverflowInsteadOfWrappingAround() {
        assertThatThrownBy(() -> calculate(1, Long.MAX_VALUE, List.of(
                event(BASE_DATE, EventDirection.INFLOW, 1L, "overflow"))))
                .isInstanceOf(ArithmeticException.class);
    }

    private CashflowBalanceProjection calculate(int horizonDays, long startingBalance, List<CashflowEvent> events) {
        return calculator.calculate(
                FORECAST_ID, new CashflowForecastPeriod(BASE_DATE, horizonDays), startingBalance, events);
    }

    private CashflowEvent event(LocalDate date, EventDirection direction, long amount, String key) {
        return CashflowEvent.of(FORECAST_ID, date, "TEST", "테스트", direction, amount, "TEST", "1", key);
    }
}
