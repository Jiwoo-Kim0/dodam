package com.ssafy.dodam.domain.pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.ssafy.dodam.domain.pattern.entity.LiquidityLevel;
import com.ssafy.dodam.domain.pattern.repository.JdbcPatternFeatureReader.LiquidityRow;
import com.ssafy.dodam.domain.pattern.repository.JdbcPatternFeatureReader.MonthlyPatternRow;
import com.ssafy.dodam.domain.pattern.service.ClusterFeature;
import com.ssafy.dodam.domain.pattern.service.PatternFeatureCalculator;
import com.ssafy.dodam.domain.pattern.service.PatternFeatures;
import com.ssafy.dodam.domain.pattern.service.PatternFeatures.IncomeBasis;
import com.ssafy.dodam.domain.pattern.service.PatternWindow;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class PatternFeatureCalculatorTest {

    private final PatternFeatureCalculator calculator = new PatternFeatureCalculator();
    private final PatternWindow window = PatternWindow.endingBefore(LocalDate.of(2026, 9, 14), 6);

    @Test
    void 기간은_기준일이_속한_달을_빼고_완료된_달로_잡는다() {
        assertThat(window.start()).isEqualTo(YearMonth.of(2026, 3));
        assertThat(window.end()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(window.months()).isEqualTo(6);
        assertThat(window.startDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(window.endDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void 지표를_정의대로_계산하고_거래_없는_달은_0으로_채운다() {
        // 4개월 데이터(5·6월 없음): 소득 300만, 소비 180만(고정 100만 = 주거 70 + 대출 30, 변동 80, 재량 40), 저축 100만
        List<MonthlyPatternRow> rows = List.of(
                row(2026, 3, 3_000_000, 1_800_000, 1_000_000, 800_000, 400_000, 1_000_000, 300_000, 6),
                row(2026, 4, 3_000_000, 1_800_000, 1_000_000, 800_000, 400_000, 1_000_000, 300_000, 6),
                row(2026, 7, 3_000_000, 1_800_000, 1_000_000, 800_000, 400_000, 0, 300_000, 5),
                row(2026, 8, 3_000_000, 1_800_000, 1_000_000, 800_000, 400_000, 1_000_000, 300_000, 6));

        PatternFeatures features = calculator.calculate(window, rows, new LiquidityRow(6_000_000, 2), 2_500_000L);

        assertThat(features.months()).hasSize(6);
        assertThat(features.months().get(2).transactionCount()).isZero();
        assertThat(features.monthsWithData()).isEqualTo(4);
        assertThat(features.dataPointCount()).isEqualTo(23);
        assertThat(features.incomeBasis()).isEqualTo(IncomeBasis.TRANSACTIONS);
        assertThat(features.monthlyIncome()).isEqualTo(3_000_000L);
        assertThat(features.averages().spending()).isEqualTo(1_800_000L);
        assertThat(features.spendingRegularity()).isEqualTo(1.0);
        assertThat(features.spendingVolatility()).isEqualTo(0.0);
        assertThat(features.savingConsistency()).isEqualTo(0.75);
        assertThat(features.fixedExpenseRatio()).isCloseTo(0.5556, within(0.0001));
        assertThat(features.variableExpenseRatio()).isCloseTo(0.4444, within(0.0001));
        assertThat(features.discretionarySpendingRatio()).isCloseTo(0.2222, within(0.0001));
        assertThat(features.debtBurden()).isEqualTo(0.1);
        assertThat(features.cashflowStability()).isEqualTo(1.0);
        assertThat(features.liquidBalance()).isEqualTo(6_000_000L);
        assertThat(features.runwayMonths()).isCloseTo(3.3333, within(0.0001));
        assertThat(features.liquidityLevel()).isEqualTo(LiquidityLevel.HIGH);

        assertThat(features.hasClusterVector()).isTrue();
        var vector = features.clusterFeatureValues();
        assertThat(vector.get(ClusterFeature.SPENDING_TO_INCOME_RATIO)).isEqualTo(0.6);
        assertThat(vector.get(ClusterFeature.SAVING_RATE)).isEqualTo(0.25);
        assertThat(vector.get(ClusterFeature.SPENDING_VOLATILITY)).isEqualTo(0.0);
    }

    @Test
    void 소득_거래가_없으면_기본정보_소득을_쓰고_둘_다_없으면_소득_지표가_null이다() {
        List<MonthlyPatternRow> rows = List.of(
                row(2026, 7, 0, 1_000_000, 0, 1_000_000, 0, 0, 0, 3),
                row(2026, 8, 0, 1_500_000, 0, 1_500_000, 0, 0, 0, 3));

        PatternFeatures fromProfile = calculator.calculate(window, rows, null, 2_000_000L);
        assertThat(fromProfile.incomeBasis()).isEqualTo(IncomeBasis.PROFILE);
        assertThat(fromProfile.monthlyIncome()).isEqualTo(2_000_000L);
        assertThat(fromProfile.cashflowStability()).isEqualTo(1.0);
        assertThat(fromProfile.debtBurden()).isEqualTo(0.0);
        assertThat(fromProfile.liquidityLevel()).isNull();
        assertThat(fromProfile.spendingVolatility()).isEqualTo(0.2);
        assertThat(fromProfile.spendingRegularity()).isEqualTo(0.8);

        PatternFeatures none = calculator.calculate(window, rows, new LiquidityRow(0, 0), null);
        assertThat(none.incomeBasis()).isEqualTo(IncomeBasis.NONE);
        assertThat(none.monthlyIncome()).isNull();
        assertThat(none.debtBurden()).isNull();
        assertThat(none.cashflowStability()).isNull();
        assertThat(none.hasClusterVector()).isFalse();
    }

    @Test
    void 모집단_특징은_개월_수와_소득_조건을_만족할_때만_만든다() {
        List<MonthlyPatternRow> twoMonths = List.of(
                row(2026, 7, 3_000_000, 1_000_000, 0, 1_000_000, 0, 0, 0, 3),
                row(2026, 8, 3_000_000, 1_000_000, 0, 1_000_000, 0, 0, 0, 3));
        assertThat(calculator.clusterVector(window, twoMonths, 3)).isEmpty();
        assertThat(calculator.clusterVector(window, twoMonths, 2)).isPresent();

        List<MonthlyPatternRow> noIncome = List.of(
                row(2026, 6, 0, 1_000_000, 0, 1_000_000, 0, 0, 0, 3),
                row(2026, 7, 0, 1_000_000, 0, 1_000_000, 0, 0, 0, 3),
                row(2026, 8, 0, 1_000_000, 0, 1_000_000, 0, 0, 0, 3));
        assertThat(calculator.clusterVector(window, noIncome, 3)).isEmpty();
    }

    @Test
    void 데이터가_없으면_빈_결과다() {
        PatternFeatures features = calculator.calculate(window, List.of(), new LiquidityRow(0, 0), null);

        assertThat(features.hasData()).isFalse();
        assertThat(features.monthsWithData()).isZero();
        assertThat(features.spendingRegularity()).isNull();
        assertThat(features.savingConsistency()).isNull();
        assertThat(features.hasClusterVector()).isFalse();
    }

    private static MonthlyPatternRow row(int year, int month, long income, long spending, long fixed, long variable,
            long discretionary, long saving, long loan, long count) {
        return new MonthlyPatternRow(1L, YearMonth.of(year, month), income, spending, fixed, variable, discretionary, saving, loan, count);
    }
}
