package com.ssafy.dodam.domain.cashflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.dodam.domain.cashflow.dto.CashflowBalanceProjection;
import com.ssafy.dodam.domain.cashflow.dto.CashflowForecastPeriod;
import com.ssafy.dodam.domain.cashflow.dto.CashflowRiskAssessment;
import com.ssafy.dodam.domain.cashflow.entity.CashflowEvent;
import com.ssafy.dodam.domain.cashflow.entity.CashflowRisk;
import com.ssafy.dodam.domain.cashflow.entity.EventDirection;
import com.ssafy.dodam.domain.cashflow.entity.RiskSeverity;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CashflowRiskDetectorTest {

    private static final Long FORECAST_ID = 91L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 9, 10);

    private final CashflowDailyBalanceCalculator calculator = new CashflowDailyBalanceCalculator();
    private final CashflowRiskDetector detector = new CashflowRiskDetector();

    @Test
    void reportsNoRiskWhenEveryScheduledPaymentIsCovered() {
        CashflowRiskAssessment assessment = assess(3, 1_000_000L, List.of(
                outflow(BASE_DATE.plusDays(1), 300_000L, "카드대금", "k1")));

        assertThat(assessment.risks()).isEmpty();
        assertThat(assessment.availableToday()).isEqualTo(700_000L);
    }

    @Test
    void detectsShortageAtThePaymentThatCannotBeCovered() {
        CashflowRiskAssessment assessment = assess(3, 100_000L, List.of(
                outflow(BASE_DATE.plusDays(1), 400_000L, "카드대금", "k1")));

        assertThat(assessment.risks()).hasSize(1);
        CashflowRisk risk = assessment.risks().get(0);
        assertThat(risk.getForecastId()).isEqualTo(FORECAST_ID);
        assertThat(risk.getRiskDate()).isEqualTo(BASE_DATE.plusDays(1));
        assertThat(risk.getType()).isEqualTo(CashflowRiskDetector.TYPE_BALANCE_SHORTAGE);
        // 출금 직전 잔액을 남긴다. 마감 잔액(-300,000)이 아니다.
        assertThat(risk.getExpectedBalance()).isEqualTo(100_000L);
        assertThat(risk.getMessage()).contains("카드대금", "400,000원", "300,000원");
    }

    /** 같은 날 입금을 먼저 반영하므로 급여가 들어오는 날의 카드대금은 위험이 아니다. */
    @Test
    void doesNotFlagPaymentCoveredByTheSameDayIncome() {
        CashflowRiskAssessment assessment = assess(1, 100_000L, List.of(
                inflow(BASE_DATE, 2_000_000L, "급여", "in1"),
                outflow(BASE_DATE, 400_000L, "카드대금", "k1")));

        assertThat(assessment.risks()).isEmpty();
    }

    /** 하루에 두 건이 빠져나갈 때 처음 막히는 출금만 잡아야 한다. */
    @Test
    void flagsOnlyThePaymentsThatActuallyRunOut() {
        CashflowRiskAssessment assessment = assess(1, 500_000L, List.of(
                outflow(BASE_DATE, 300_000L, "관리비", "k1"),
                outflow(BASE_DATE, 400_000L, "카드대금", "k2")));

        assertThat(assessment.risks()).hasSize(1);
        assertThat(assessment.risks().get(0).getMessage()).contains("카드대금");
        assertThat(assessment.risks().get(0).getExpectedBalance()).isEqualTo(200_000L);
    }

    @Test
    void gradesSeverityByHowMuchOfThePaymentIsMissing() {
        // 부족 20% 미만
        assertThat(severityOf(900_000L, 1_000_000L)).isEqualTo(RiskSeverity.LOW);
        // 부족 20%
        assertThat(severityOf(800_000L, 1_000_000L)).isEqualTo(RiskSeverity.MEDIUM);
        // 부족 50%
        assertThat(severityOf(500_000L, 1_000_000L)).isEqualTo(RiskSeverity.HIGH);
        // 잔액이 0이면 한 푼도 못 낸다
        assertThat(severityOf(0L, 1_000_000L)).isEqualTo(RiskSeverity.CRITICAL);
    }

    /** 이미 잔액이 음수인 상태의 출금은 비율이 100%를 넘어 구간이 무의미하므로 CRITICAL로 고정한다. */
    @Test
    void gradesPaymentOnAlreadyNegativeBalanceAsCritical() {
        CashflowRiskAssessment assessment = assess(1, 0L, List.of(
                outflow(BASE_DATE, 100_000L, "관리비", "k1"),
                outflow(BASE_DATE, 100_000L, "카드대금", "k2")));

        assertThat(assessment.risks()).extracting(CashflowRisk::getSeverity)
                .containsExactly(RiskSeverity.CRITICAL, RiskSeverity.CRITICAL);
    }

    @Test
    void usesTheMinimumBalanceOfTheWholeHorizonAsAvailableToday() {
        // 오늘 마감은 1,000,000원이지만 이틀 뒤 400,000원까지 떨어진다.
        CashflowRiskAssessment assessment = assess(3, 1_000_000L, List.of(
                outflow(BASE_DATE.plusDays(2), 600_000L, "카드대금", "k1")));

        assertThat(assessment.availableToday()).isEqualTo(400_000L);
    }

    @Test
    void clampsAvailableTodayAtZeroWhenTheForecastGoesNegative() {
        CashflowRiskAssessment assessment = assess(2, 100_000L, List.of(
                outflow(BASE_DATE.plusDays(1), 400_000L, "카드대금", "k1")));

        assertThat(assessment.availableToday()).isZero();
    }

    private RiskSeverity severityOf(long startingBalance, long paymentAmount) {
        CashflowRiskAssessment assessment = assess(1, startingBalance, List.of(
                outflow(BASE_DATE, paymentAmount, "카드대금", "k1")));
        return assessment.risks().get(0).getSeverity();
    }

    private CashflowRiskAssessment assess(int horizonDays, long startingBalance, List<CashflowEvent> events) {
        CashflowBalanceProjection projection = calculator.calculate(
                FORECAST_ID, new CashflowForecastPeriod(BASE_DATE, horizonDays), startingBalance, events);
        return detector.assess(FORECAST_ID, projection);
    }

    private CashflowEvent outflow(LocalDate date, long amount, String name, String key) {
        return CashflowEvent.of(
                FORECAST_ID, date, "CARD_PAYMENT", name, EventDirection.OUTFLOW, amount, "CARD", "1", key);
    }

    private CashflowEvent inflow(LocalDate date, long amount, String name, String key) {
        return CashflowEvent.of(
                FORECAST_ID, date, "ACCOUNT_SCHEDULE", name, EventDirection.INFLOW, amount, "ACCOUNT", "1", key);
    }
}
