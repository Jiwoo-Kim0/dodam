package com.ssafy.dodam.domain.deposit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.global.policy.EarlyTerminationRatePolicy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 기준일·중도해지이율 구간표·이자 계산의 단위 검증.
 *
 * <p>구간표는 근거 자료가 없어 서비스가 정한 기본안이므로 경계값을 API 통합 테스트와 별도로 고정한다.
 */
class DepositCalculationTest {
    private final DepositInterestCalculator calculator = new DepositInterestCalculator();

    @Test
    void seoulMidnightDeterminesDateEvenWhenClockUsesUtc() {
        var clock = new DepositClock(Clock.fixed(Instant.parse("2024-02-29T15:00:00Z"), ZoneOffset.UTC));
        assertThat(clock.today()).isEqualTo(LocalDate.of(2024, 3, 1));
    }

    /**
     * 세전이자 = 원금 × 연이율 ÷ 100 × 일수 ÷ 365, 원 단위 절사.
     *
     * <p>세액도 절사하므로 언제나 세전 = 세액 + 세후가 성립한다. 세후를 먼저 절사하면 1원이 어긋난다.
     */
    @ParameterizedTest
    @CsvSource({
            // 원금, 이율, 일수, 세전, 세액, 세후
            "10000000, 3.15, 365, 315000, 48510, 266490",
            "10000000, 1.26, 251, 86646, 13343, 73303",
            // 일수 0은 이자 0이다. 계산 불가와 구분한다.
            "10000000, 3.15, 0, 0, 0, 0",
            // 이율 0도 유효한 값이다.
            "10000000, 0.00, 365, 0, 0, 0",
            // 1원 미만 이자는 절사돼 0이 된다. 반올림으로 1원을 만들지 않는다.
            "1000, 0.10, 3, 0, 0, 0",
            // 윤년에도 365일로 나눈다.
            "10000000, 3.15, 366, 315863, 48642, 267221"
    })
    void simpleInterestTruncatesToWonAndKeepsGrossEqualToTaxPlusNet(
            long principal, String rate, long days, long gross, long tax, long net
    ) {
        var interest = calculator.calculate(principal, new BigDecimal(rate), days);
        assertThat(interest.grossInterest()).isEqualTo(gross);
        assertThat(interest.tax()).isEqualTo(tax);
        assertThat(interest.netInterest()).isEqualTo(net);
        assertThat(interest.grossInterest()).isEqualTo(interest.tax() + interest.netInterest());
        assertThat(interest.payoutAmount()).isEqualTo(principal + net);
    }

    /** 경과 개월 구간의 양쪽 경계를 모두 고정한다. 구간이 바뀌면 예상 해지금액이 통째로 달라진다. */
    @ParameterizedTest
    @CsvSource({
            "0, 0.1000, ELAPSED_MONTHS_LT_1",
            "1, 0.6000, ELAPSED_MONTHS_GTE_1",
            "2, 0.6000, ELAPSED_MONTHS_GTE_1",
            "3, 0.9000, ELAPSED_MONTHS_GTE_3",
            "5, 0.9000, ELAPSED_MONTHS_GTE_3",
            "6, 1.2000, ELAPSED_MONTHS_GTE_6",
            "8, 1.2000, ELAPSED_MONTHS_GTE_6",
            "9, 1.8000, ELAPSED_MONTHS_GTE_9",
            "10, 1.8000, ELAPSED_MONTHS_GTE_9",
            "11, 2.4000, ELAPSED_MONTHS_GTE_11",
            // 만기를 넘겨 유지해도 계약이율을 넘지 않는다. 최고 구간은 계약이율의 80%다.
            "36, 2.4000, ELAPSED_MONTHS_GTE_11"
    })
    void earlyTerminationRateFollowsTheElapsedMonthTiers(
            long elapsedMonths, String expectedRate, String expectedTier
    ) {
        assertThat(EarlyTerminationRatePolicy.resolve(new BigDecimal("3.00"), elapsedMonths))
                .isEqualByComparingTo(new BigDecimal(expectedRate));
        assertThat(EarlyTerminationRatePolicy.describe(elapsedMonths)).isEqualTo(expectedTier);
    }

    /** 비율을 곱한 값이 최저이율보다 낮아지는 저금리 계약에서도 연 0.10%는 보장한다. */
    @Test
    void earlyTerminationRateNeverFallsBelowTheFloor() {
        assertThat(EarlyTerminationRatePolicy.resolve(new BigDecimal("0.10"), 36))
                .isEqualByComparingTo(new BigDecimal("0.1000"));
        assertThat(EarlyTerminationRatePolicy.resolve(BigDecimal.ZERO, 36))
                .isEqualByComparingTo(new BigDecimal("0.1000"));
    }

    @Test
    void amountsBeyondLongRangeThrowInsteadOfWrappingAround() {
        assertThatThrownBy(() -> calculator.calculate(Long.MAX_VALUE, new BigDecimal("3.15"), 365))
                .isInstanceOf(ArithmeticException.class);
    }
}
