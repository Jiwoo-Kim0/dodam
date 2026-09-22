package com.ssafy.dodam.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.dodam.domain.report.dto.EfficiencyScoreResponse;
import com.ssafy.dodam.domain.report.dto.EfficiencyScoreResponse.Dimension;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 금융 효율 점수 산식(S15P21D105-635). 이 테스트가 곧 산식 문서다 — 임계값을 바꾸면 여기와 {@code EfficiencyScorePolicy.VERSION}을
 * 함께 바꾼다.
 */
class EfficiencyScorePolicyTest {

    @Test
    void spendingIsFullAtHalfOfIncomeAndZeroAtIncome() {
        assertThat(EfficiencyScorePolicy.spending(1_450_000, 3_200_000).score()).isEqualTo(100); // 0.4531 ≤ 0.5
        assertThat(EfficiencyScorePolicy.spending(2_400_000, 3_200_000).score()).isEqualTo(50);  // 0.75
        assertThat(EfficiencyScorePolicy.spending(3_500_000, 3_200_000).score()).isEqualTo(0);   // 1.09
        Dimension dimension = EfficiencyScorePolicy.spending(2_304_000, 3_200_000);            // 0.72
        assertThat(dimension.score()).isEqualTo(56);
        assertThat(dimension.status()).isEqualTo("NORMAL");
        assertThat(dimension.description()).isEqualTo("소득 대비 소비율이 72%예요.");
        assertThat(dimension.value()).isEqualTo(0.72);
        assertThat(dimension.valueType()).isEqualTo("RATIO");
    }

    @Test
    void savingIsFullAtThirtyPercentOfIncome() {
        assertThat(EfficiencyScorePolicy.saving(0, 3_200_000).score()).isEqualTo(0);
        assertThat(EfficiencyScorePolicy.saving(400_000, 3_200_000).score()).isEqualTo(42);      // 0.125
        assertThat(EfficiencyScorePolicy.saving(768_000, 3_200_000).score()).isEqualTo(80);      // 0.24
        assertThat(EfficiencyScorePolicy.saving(1_000_000, 3_200_000).score()).isEqualTo(100);   // 0.3125
        assertThat(EfficiencyScorePolicy.saving(768_000, 3_200_000).description()).isEqualTo("소득의 24%를 저축했어요.");
    }

    @Test
    void incomeMissingMakesIncomeBasedDimensionsUnavailableNotZero() {
        Dimension spending = EfficiencyScorePolicy.spending(500_000, 0);
        assertThat(spending.score()).isNull();
        assertThat(spending.status()).isEqualTo("UNAVAILABLE");
        assertThat(spending.unavailableReason()).isEqualTo("INCOME_MISSING");
        assertThat(EfficiencyScorePolicy.saving(0, 0).unavailableReason()).isEqualTo("INCOME_MISSING");
    }

    @Test
    void fixedExpenseShareIsFullBelowFortyPercentAndZeroAboveEighty() {
        assertThat(EfficiencyScorePolicy.fixedExpense(300_000, 1_000_000).score()).isEqualTo(100);
        assertThat(EfficiencyScorePolicy.fixedExpense(400_000, 1_000_000).score()).isEqualTo(100);
        assertThat(EfficiencyScorePolicy.fixedExpense(480_000, 1_000_000).score()).isEqualTo(80);
        assertThat(EfficiencyScorePolicy.fixedExpense(900_000, 1_450_000).score()).isEqualTo(45);  // 0.6207
        assertThat(EfficiencyScorePolicy.fixedExpense(700_000, 1_000_000).score()).isEqualTo(25);
        assertThat(EfficiencyScorePolicy.fixedExpense(800_000, 1_000_000).score()).isEqualTo(0);
        assertThat(EfficiencyScorePolicy.fixedExpense(900_000, 1_000_000).score()).isEqualTo(0);
        assertThat(EfficiencyScorePolicy.fixedExpense(0, 0).unavailableReason()).isEqualTo("NO_SPENDING");
    }

    @Test
    void creditUsesGradeTableAndTellsMissingApart() {
        assertThat(EfficiencyScorePolicy.credit(768, null).score()).isEqualTo(77);
        assertThat(EfficiencyScorePolicy.credit(768, null).value()).isEqualTo(768.0);
        assertThat(EfficiencyScorePolicy.credit(768, null).valueType()).isEqualTo("SCORE");
        assertThat(EfficiencyScorePolicy.credit(null, "A").score()).isEqualTo(100);
        assertThat(EfficiencyScorePolicy.credit(null, "B").score()).isEqualTo(85);
        assertThat(EfficiencyScorePolicy.credit(null, "c").score()).isEqualTo(70);
        assertThat(EfficiencyScorePolicy.credit(null, "D").score()).isEqualTo(50);
        assertThat(EfficiencyScorePolicy.credit(null, "E").score()).isEqualTo(30);
        assertThat(EfficiencyScorePolicy.credit(null, "A").grade()).isEqualTo("A");
        assertThat(EfficiencyScorePolicy.credit(null, "A").valueType()).isEqualTo("GRADE");
        Dimension missing = EfficiencyScorePolicy.credit(null, null);
        assertThat(missing.score()).isNull();
        assertThat(missing.unavailableReason()).isEqualTo("CREDIT_SCORE_MISSING");
        assertThat(EfficiencyScorePolicy.credit(null, "Z").unavailableReason()).isEqualTo("CREDIT_SCORE_MISSING");
    }

    @Test
    void cashflowSubtractsPerRiskAndNeverGoesBelowZero() {
        assertThat(EfficiencyScorePolicy.cashflow(true, false, List.of()).score()).isEqualTo(100);
        Dimension one = EfficiencyScorePolicy.cashflow(true, false, List.of("HIGH"));
        assertThat(one.score()).isEqualTo(70);
        assertThat(one.description()).isEqualTo("예정 잔액 위험 1건이 있어요.");
        assertThat(one.value()).isEqualTo(1.0);
        assertThat(one.valueType()).isEqualTo("COUNT");
        assertThat(EfficiencyScorePolicy.cashflow(true, false, List.of("CRITICAL", "CRITICAL", "HIGH")).score()).isEqualTo(0);
        assertThat(EfficiencyScorePolicy.cashflow(false, false, List.of()).unavailableReason()).isEqualTo("FORECAST_NOT_FOUND");
    }

    @Test
    void inefficiencySubtractsPerIssue() {
        assertThat(EfficiencyScorePolicy.inefficiency(true, false, List.of()).score()).isEqualTo(100);
        Dimension two = EfficiencyScorePolicy.inefficiency(true, false, List.of("HIGH", "MEDIUM"));
        assertThat(two.score()).isEqualTo(60);
        assertThat(two.status()).isEqualTo("NORMAL");
        assertThat(two.description()).isEqualTo("점검이 필요한 항목이 2건 있어요.");
        assertThat(EfficiencyScorePolicy.inefficiency(false, false, List.of()).unavailableReason()).isEqualTo("DIAGNOSIS_NOT_FOUND");
    }

    @Test
    void overallAveragesOnlyScoredDimensionsAndKeepsOrder() {
        List<Dimension> dimensions = EfficiencyScorePolicy.ordered(
                EfficiencyScorePolicy.spending(1_450_000, 3_200_000),   // 100
                EfficiencyScorePolicy.saving(400_000, 3_200_000),       // 42
                EfficiencyScorePolicy.fixedExpense(900_000, 1_450_000), // 45
                EfficiencyScorePolicy.credit(null, null),               // unavailable
                EfficiencyScorePolicy.cashflow(false, false, List.of()),       // unavailable
                EfficiencyScorePolicy.inefficiency(false, false, List.of()));  // unavailable
        EfficiencyScoreResponse overall = EfficiencyScorePolicy.overall(dimensions);
        assertThat(overall.score()).isEqualTo(62); // (100 + 42 + 45) / 3 = 62.3 → 62
        assertThat(overall.maxScore()).isEqualTo(100);
        assertThat(overall.availableDimensionCount()).isEqualTo(3);
        assertThat(overall.unavailableReason()).isNull();
        assertThat(overall.previousMonthDifference()).isNull();
        assertThat(overall.calculationVersion()).isEqualTo("efficiency-v3");
        assertThat(overall.dimensions()).extracting(Dimension::key)
                .containsExactly("SPENDING", "SAVING", "FIXED_EXPENSE", "CREDIT", "CASHFLOW", "INEFFICIENCY");
        assertThat(overall.summary()).isEqualTo("지출 관리는 안정적이에요. 저축을 조금 더 챙겨 보세요.");
    }

    @Test
    void fallbackRunsSayTheyAreTheLatestNotTheMonths() {
        // 그 달 말일까지 실행이 없어 생성 시점 최신을 쓰면 근거 문장에 밝힌다. 점수 산식은 같다
        Dimension cashflow = EfficiencyScorePolicy.cashflow(true, true, List.of("HIGH"));
        assertThat(cashflow.score()).isEqualTo(70);
        assertThat(cashflow.description()).isEqualTo("가장 최근 예측 기준으로 예정 잔액 위험 1건이 있어요.");
        Dimension inefficiency = EfficiencyScorePolicy.inefficiency(true, true, List.of());
        assertThat(inefficiency.score()).isEqualTo(100);
        assertThat(inefficiency.description()).isEqualTo("가장 최근 진단 기준으로 점검이 필요한 항목이 없어요.");
    }

    @Test
    void overallIsNullWhenFewerThanThreeDimensionsAreScored() {
        List<Dimension> dimensions = EfficiencyScorePolicy.ordered(
                EfficiencyScorePolicy.spending(0, 0),
                EfficiencyScorePolicy.saving(0, 0),
                EfficiencyScorePolicy.fixedExpense(0, 0),
                EfficiencyScorePolicy.credit(1000, null),
                EfficiencyScorePolicy.cashflow(true, false, List.of()),
                EfficiencyScorePolicy.inefficiency(false, false, List.of()));
        EfficiencyScoreResponse overall = EfficiencyScorePolicy.overall(dimensions);
        assertThat(overall.score()).isNull();
        assertThat(overall.availableDimensionCount()).isEqualTo(2);
        assertThat(overall.unavailableReason()).isEqualTo("INSUFFICIENT_DIMENSIONS");
        assertThat(overall.summary()).isEqualTo("점수를 계산할 데이터가 아직 부족해요.");
    }

    @Test
    void summaryDependsOnTheWeakestDimension() {
        List<Dimension> allGood = EfficiencyScorePolicy.ordered(
                EfficiencyScorePolicy.spending(1_000_000, 3_200_000), EfficiencyScorePolicy.saving(1_000_000, 3_200_000),
                EfficiencyScorePolicy.fixedExpense(200_000, 1_000_000), EfficiencyScorePolicy.credit(1000, null),
                EfficiencyScorePolicy.cashflow(true, false, List.of()), EfficiencyScorePolicy.inefficiency(true, false, List.of()));
        assertThat(EfficiencyScorePolicy.overall(allGood).summary()).isEqualTo("전반적으로 금융 생활이 안정적이에요.");

        List<Dimension> oneNormal = EfficiencyScorePolicy.ordered(
                EfficiencyScorePolicy.spending(1_000_000, 3_200_000), EfficiencyScorePolicy.saving(1_000_000, 3_200_000),
                EfficiencyScorePolicy.fixedExpense(200_000, 1_000_000), EfficiencyScorePolicy.credit(1000, null),
                EfficiencyScorePolicy.cashflow(true, false, List.of()), EfficiencyScorePolicy.inefficiency(true, false, List.of("HIGH", "MEDIUM")));
        assertThat(EfficiencyScorePolicy.overall(oneNormal).summary()).isEqualTo("지출 관리는 안정적이에요. 비효율 관리를 조금 더 챙겨 보세요.");
    }

    @Test
    void koreanParticlesFollowTheFinalConsonant() {
        assertThat(EfficiencyScorePolicy.topic("저축")).isEqualTo("은");
        assertThat(EfficiencyScorePolicy.topic("지출 관리")).isEqualTo("는");
        assertThat(EfficiencyScorePolicy.object("현금흐름")).isEqualTo("을");
        assertThat(EfficiencyScorePolicy.object("비효율 관리")).isEqualTo("를");
    }

    @Test
    void statusBands() {
        assertThat(EfficiencyScorePolicy.status(70)).isEqualTo("GOOD");
        assertThat(EfficiencyScorePolicy.status(69)).isEqualTo("NORMAL");
        assertThat(EfficiencyScorePolicy.status(40)).isEqualTo("NORMAL");
        assertThat(EfficiencyScorePolicy.status(39)).isEqualTo("CHECK");
    }
}
