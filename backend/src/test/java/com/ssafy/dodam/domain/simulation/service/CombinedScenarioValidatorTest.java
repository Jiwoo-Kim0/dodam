package com.ssafy.dodam.domain.simulation.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.domain.simulation.dto.CashAdvanceInput;
import com.ssafy.dodam.domain.simulation.dto.InstallmentInput;
import com.ssafy.dodam.domain.simulation.dto.LoanPrepaymentInput;
import com.ssafy.dodam.domain.simulation.dto.LoanRefinanceInput;
import com.ssafy.dodam.domain.simulation.dto.RevolvingInput;
import com.ssafy.dodam.domain.simulation.dto.SavingChangeInput;
import com.ssafy.dodam.domain.simulation.dto.SimulationInput;
import com.ssafy.dodam.domain.simulation.dto.SpendingReductionInput;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 586 §4 효과 중복·충돌 표. 검증은 기준일 생략값이 채워지기 전에 돌아야 하므로 추가상환일 없는 입력으로 확인한다. */
class CombinedScenarioValidatorTest {
    private static void assertCode(List<SimulationInput> children, ErrorCode code) {
        assertThatThrownBy(() -> CombinedScenarioValidator.validate(children)).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(code);
    }

    @Test
    void rejectsDuplicatedActionsIncludingFeeModeBypass() {
        assertCode(List.of(InstallmentInput.of(1_200_000L, 12, null), InstallmentInput.of(1_200_000L, 12, BigDecimal.ZERO)),
                ErrorCode.SIMULATION_COMBINED_DUPLICATE_SCENARIO);
        assertCode(List.of(InstallmentInput.of(1L, 12, new BigDecimal("1")), InstallmentInput.of(1L, 12, new BigDecimal("1.0000"))),
                ErrorCode.SIMULATION_COMBINED_DUPLICATE_SCENARIO);
        assertCode(List.of(CashAdvanceInput.of(1L, BigDecimal.ONE, null), CashAdvanceInput.of(1L, BigDecimal.ONE, 1)),
                ErrorCode.SIMULATION_COMBINED_DUPLICATE_SCENARIO);
        assertCode(List.of(new SpendingReductionInput("FOOD", 1L, 12), new SpendingReductionInput("FOOD", 1L, 12)),
                ErrorCode.SIMULATION_COMBINED_DUPLICATE_SCENARIO);
        // 금액·기간이 다르면 별개의 가상 행동
        assertThatCode(() -> CombinedScenarioValidator.validate(List.of(InstallmentInput.of(1L, 12, null), InstallmentInput.of(2L, 12, null),
                CashAdvanceInput.of(1L, BigDecimal.ONE, 1), CashAdvanceInput.of(1L, BigDecimal.ONE, 2)))).doesNotThrowAnyException();
    }

    @Test
    void rejectsSameTargetConflicts() {
        assertCode(List.of(new SpendingReductionInput(null, 1L, 12), new SpendingReductionInput("FOOD", 1L, 12)),
                ErrorCode.SIMULATION_COMBINED_SCENARIO_CONFLICT);
        assertCode(List.of(new SpendingReductionInput("FOOD", 1L, 12), new SpendingReductionInput(null, 1L, 12)),
                ErrorCode.SIMULATION_COMBINED_SCENARIO_CONFLICT);
        assertCode(List.of(new SpendingReductionInput("FOOD", 1L, 12), new SpendingReductionInput("FOOD", 2L, 6)),
                ErrorCode.SIMULATION_COMBINED_SCENARIO_CONFLICT);
        assertCode(List.of(new SavingChangeInput(null, 1L, 12), new SavingChangeInput(5L, 2L, 12)),
                ErrorCode.SIMULATION_COMBINED_SCENARIO_CONFLICT);
        assertCode(List.of(new LoanPrepaymentInput(7L, 1L, null), new LoanRefinanceInput(7L, 20L, null)),
                ErrorCode.SIMULATION_COMBINED_SCENARIO_CONFLICT);
        assertCode(List.of(new LoanPrepaymentInput(7L, 1L, null), new LoanPrepaymentInput(7L, 2L, null)),
                ErrorCode.SIMULATION_COMBINED_SCENARIO_CONFLICT);
        assertCode(List.of(new RevolvingInput(5L, 1L, BigDecimal.TEN, BigDecimal.ONE), new RevolvingInput(5L, 2L, BigDecimal.TEN, BigDecimal.ONE)),
                ErrorCode.SIMULATION_COMBINED_SCENARIO_CONFLICT);
        assertCode(List.of(new RevolvingInput(null, 1L, BigDecimal.TEN, BigDecimal.ONE), new RevolvingInput(5L, 2L, BigDecimal.TEN, BigDecimal.ONE)),
                ErrorCode.SIMULATION_COMBINED_SCENARIO_CONFLICT);
        assertCode(List.of(new RevolvingInput(5L, 1L, BigDecimal.TEN, BigDecimal.ONE), new RevolvingInput(null, 2L, BigDecimal.TEN, BigDecimal.ONE)),
                ErrorCode.SIMULATION_COMBINED_SCENARIO_CONFLICT);
        // 허용: 서로 다른 카테고리, SPENDING+SAVING, 다른 대출, 다른 카드
        assertThatCode(() -> CombinedScenarioValidator.validate(List.of(new SpendingReductionInput("FOOD", 1L, 12),
                new SpendingReductionInput("CAFE", 1L, 12), new SavingChangeInput(null, 1L, 12), new LoanPrepaymentInput(7L, 1L, null),
                new LoanRefinanceInput(8L, 20L, null), new RevolvingInput(5L, 1L, BigDecimal.TEN, BigDecimal.ONE),
                new RevolvingInput(6L, 1L, BigDecimal.TEN, BigDecimal.ONE)))).doesNotThrowAnyException();
    }
}
