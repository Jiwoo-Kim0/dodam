package com.ssafy.dodam.domain.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.domain.simulation.dto.SavingChangeInput;
import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult;
import com.ssafy.dodam.domain.simulation.dto.SpendingReductionInput;
import com.ssafy.dodam.domain.simulation.service.calculator.SavingChangeCalculator;
import com.ssafy.dodam.domain.simulation.service.calculator.SpendingReductionCalculator;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** DB 없이 계산기 2종과 입력 검증만 본다. 달성일 식·지문은 550의 SimulationModelTest. */
class SimulationCalculatorTest {
    private static final LocalDate BASE = LocalDate.of(2026, 9, 13);
    private final SpendingReductionCalculator spending = new SpendingReductionCalculator();
    private final SavingChangeCalculator saving = new SavingChangeCalculator();

    // 목표 A: 남은 1,000,000 / 속도 100,000 → 10개월. 목표 B: 남은 500,000 / 속도 0 → 달성 불가
    private static SimulationBaseline baseline(Long income) {
        return new SimulationBaseline(BASE, income, 1_500_000L, Map.of("FOOD", 300_000L, "UNCATEGORIZED", 1_200_000L),
                List.of(new SimulationBaseline.SavingGoal(1L, "A", 2_000_000L, 1_000_000L, 1_000_000L, 100_000L,
                                BASE.plusYears(1), BASE.plusMonths(10), List.of(11L)),
                        new SimulationBaseline.SavingGoal(2L, "B", 500_000L, 0L, 500_000L, 0L, BASE.plusYears(1), null, List.of())),
                List.of(new SimulationBaseline.SpendingGoal(3L, "C", 1_000_000L, 600_000L, 1_380_000L)), List.of());
    }

    @Test
    void spendingReductionRaisesAvailableFundsAndAdvancesGoals() {
        SimulationResult result = spending.calculate(baseline(3_000_000L), new SpendingReductionInput(null, 150_000L, 6));

        assertThat(result.before().monthlyAvailableFunds()).isEqualTo(3_000_000L - 1_500_000L - 100_000L);
        assertThat(result.after().monthlyAvailableFunds()).isEqualTo(result.before().monthlyAvailableFunds() + 150_000L);
        assertThat(result.after().monthlySaving()).isEqualTo(100_000L); // 저축 약정은 그대로
        // 남은 금액 비례 배분: A 1,000,000 : B 500,000 → 100,000 : 50,000
        assertThat(result.after().goals()).extracting(SimulationResult.GoalForecast::monthlySaving).containsExactly(200_000L, 50_000L);
        assertThat(result.goalImpacts()).containsExactly(
                new SimulationResult.GoalImpact(1L, BASE.plusMonths(10), BASE.plusMonths(5)),
                new SimulationResult.GoalImpact(2L, null, BASE.plusMonths(10)));
        assertThat(result.cashflow().months()).hasSize(6);
        assertThat(result.cashflow().months().get(5)).isEqualTo(new SimulationResult.Month("2027-03", 150_000L, 900_000L));
        assertThat(result.comparison().assets()).singleElement().extracting(SimulationResult.Item::after).isEqualTo(900_000L);
        assertThat(result.comparison().items()).anySatisfy(item -> {
            assertThat(item.key()).isEqualTo("SPENDING_GOAL_3_PROJECTED_MONTH_END");
            assertThat(item.after()).isEqualTo(1_230_000L);
        });
        assertThat(result.assumptions()).contains("REDUCTION_DIRECTED_TO_SAVING_GOALS", "SPLIT_BY_REMAINING");
    }

    @Test
    void spendingReductionIsBoundedByScopeAverage() {
        assertThatThrownBy(() -> spending.calculate(baseline(null), new SpendingReductionInput("FOOD", 300_001L, 12)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.SIMULATION_REDUCTION_EXCEEDS_SPENDING);
        SimulationResult result = spending.calculate(baseline(null), new SpendingReductionInput("FOOD", 300_000L, 12));
        assertThat(result.before().monthlyAvailableFunds()).isNull(); // 소득 미등록
        assertThat(result.cashflow().before()).isNull();
        assertThat(result.comparison().items().get(0).after()).isEqualTo(0L);
        assertThatThrownBy(() -> spending.calculate(baseline(null), new SpendingReductionInput("TRAVEL", 1L, 12)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void savingChangeReplacesPaceAndCanDelayGoals() {
        SimulationResult raise = saving.calculate(baseline(3_000_000L), new SavingChangeInput(null, 300_000L, 12));
        assertThat(raise.after().monthlySaving()).isEqualTo(300_000L);
        assertThat(raise.after().goals()).extracting(SimulationResult.GoalForecast::monthlySaving).containsExactly(200_000L, 100_000L);
        assertThat(raise.after().monthlyAvailableFunds()).isEqualTo(3_000_000L - 1_500_000L - 300_000L);
        assertThat(raise.cashflow().months().get(0).delta()).isEqualTo(-200_000L);
        assertThat(raise.goalImpacts().get(0)).isEqualTo(new SimulationResult.GoalImpact(1L, BASE.plusMonths(10), BASE.plusMonths(5)));

        SimulationResult cut = saving.calculate(baseline(3_000_000L), new SavingChangeInput(null, 30_000L, 12));
        assertThat(cut.goalImpacts().get(0).after()).isAfter(cut.goalImpacts().get(0).before());
        assertThat(cut.cashflow().months().get(0).delta()).isEqualTo(70_000L);

        SimulationResult zero = saving.calculate(baseline(3_000_000L), new SavingChangeInput(null, 0L, 12));
        assertThat(zero.goalImpacts()).extracting(SimulationResult.GoalImpact::after).containsOnlyNulls();
    }

    @Test
    void savingChangeWithLinkedAccountTargetsOnlyThatGoal() {
        SimulationResult result = saving.calculate(baseline(null), new SavingChangeInput(11L, 250_000L, 3));
        assertThat(result.after().goals()).extracting(SimulationResult.GoalForecast::monthlySaving).containsExactly(250_000L, 0L);
        assertThat(result.after().monthlySaving()).isEqualTo(250_000L);
        assertThat(result.assumptions()).doesNotContain("SPLIT_BY_REMAINING", "ACCOUNT_NOT_LINKED_TO_GOAL");

        SimulationResult unlinked = saving.calculate(baseline(null), new SavingChangeInput(99L, 250_000L, 3));
        assertThat(unlinked.assumptions()).contains("ACCOUNT_NOT_LINKED_TO_GOAL");
        assertThat(unlinked.after().goals()).extracting(SimulationResult.GoalForecast::monthlySaving).containsExactly(166_666L, 83_334L);
    }

    @Test
    void savingChangeIsBoundedByIncomeOnlyWhenKnown() {
        assertThatThrownBy(() -> saving.calculate(baseline(200_000L), new SavingChangeInput(null, 200_001L, 12)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.SIMULATION_SAVING_EXCEEDS_INCOME);
        assertThat(saving.calculate(baseline(null), new SavingChangeInput(null, 99_000_000L, 12)).after().monthlySaving())
                .isEqualTo(99_000_000L);
    }

    @Test
    void emptyBaselineProducesEmptyImpactsNotErrors() {
        SimulationBaseline empty = new SimulationBaseline(BASE, null, 0L, Map.of(), List.of(), List.of(),
                List.of(SimulationBaseline.SPENDING_NO_TRANSACTIONS));
        SimulationResult result = saving.calculate(empty, new SavingChangeInput(null, 100_000L, 1));
        assertThat(result.goalImpacts()).isEmpty();
        assertThat(result.after().monthlySaving()).isEqualTo(100_000L);
        assertThatThrownBy(() -> spending.calculate(empty, new SpendingReductionInput(null, 1L, 1)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void inputsValidateRanges() {
        assertThatThrownBy(() -> new SpendingReductionInput(null, 0L, 12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SavingChangeInput(null, -1L, 12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SavingChangeInput(null, 1L, 121)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SavingChangeInput(null, 1L, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new SpendingReductionInput("FOOD", 10L, 12).fingerprint()).isEqualTo("FOOD|10|12")
                .isNotEqualTo(new SpendingReductionInput(null, 10L, 12).fingerprint());
        assertThat(new SavingChangeInput(7L, 10L, 3).fingerprint()).isEqualTo("7|10|3");
    }
}
