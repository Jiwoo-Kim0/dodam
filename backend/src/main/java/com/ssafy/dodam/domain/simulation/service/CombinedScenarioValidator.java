package com.ssafy.dodam.domain.simulation.service;

import com.ssafy.dodam.domain.simulation.dto.InstallmentInput;
import com.ssafy.dodam.domain.simulation.dto.LoanPrepaymentInput;
import com.ssafy.dodam.domain.simulation.dto.LoanRefinanceInput;
import com.ssafy.dodam.domain.simulation.dto.RevolvingInput;
import com.ssafy.dodam.domain.simulation.dto.SavingChangeInput;
import com.ssafy.dodam.domain.simulation.dto.SimulationInput;
import com.ssafy.dodam.domain.simulation.dto.SpendingReductionInput;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 복합 자식 간 효과 중복·충돌 검사(586 §4). 자식은 같은 기준선에서 독립 계산되므로 같은 잔액·같은 범위를 두 자식이
 * 각각 바꾸면 합계가 이중 반영이 된다. 그런 조합은 계산하지 않고 400으로 거부한다.
 *
 * <ul>
 *   <li>동일 행동 반복({@code DUPLICATE_SCENARIO}): 정규화된 입력이 같은 자식. 할부는 수수료율 모드를 빼고 비교한다 —
 *       생략과 명시적 0으로 같은 할부를 두 번 넣어 검사를 우회할 수 없다.</li>
 *   <li>같은 대상 충돌({@code SCENARIO_CONFLICT}): 전체 지출 감소와 카테고리 감소 혼합, 같은 카테고리 반복, SAVING 2개 이상,
 *       같은 loanId의 추가상환·대환(유형이 달라도), 같은 cardId 리볼빙, cardId 없는 리볼빙이 포함된 다중 리볼빙(서로 다른
 *       잔액인지 확인 불가).</li>
 * </ul>
 * 총 감소액 상한(자식 감소액 합 ≤ 평균 지출)은 기준선이 필요해 복합 계산기가 검사한다.
 */
final class CombinedScenarioValidator {
    private CombinedScenarioValidator() {
    }

    static void validate(List<SimulationInput> children) {
        Set<String> actions = new HashSet<>();
        Set<String> spendingCategories = new HashSet<>();
        boolean spendingAll = false;
        int savingCount = 0;
        Set<Long> loanIds = new HashSet<>();
        Set<Long> cardIds = new HashSet<>();
        int revolvingCount = 0;
        boolean revolvingWithoutCard = false;
        for (SimulationInput child : children) {
            if (!actions.add(child.type().name() + "|" + actionKey(child))) {
                throw new BusinessException(ErrorCode.SIMULATION_COMBINED_DUPLICATE_SCENARIO);
            }
            if (child instanceof SpendingReductionInput spending) {
                if (spending.categoryCode() == null) {
                    if (spendingAll || !spendingCategories.isEmpty()) {
                        throw conflict();
                    }
                    spendingAll = true;
                } else if (spendingAll || !spendingCategories.add(spending.categoryCode())) {
                    throw conflict();
                }
            } else if (child instanceof SavingChangeInput) {
                if (++savingCount > 1) {
                    throw conflict();
                }
            } else if (child instanceof LoanPrepaymentInput prepayment) {
                if (!loanIds.add(prepayment.loanId())) {
                    throw conflict();
                }
            } else if (child instanceof LoanRefinanceInput refinance) {
                if (!loanIds.add(refinance.loanId())) {
                    throw conflict();
                }
            } else if (child instanceof RevolvingInput revolving) {
                revolvingCount++;
                if (revolving.cardId() == null) {
                    revolvingWithoutCard = true;
                } else if (!cardIds.add(revolving.cardId())) {
                    throw conflict();
                }
                if (revolvingWithoutCard && revolvingCount > 1) {
                    throw conflict();
                }
            }
        }
    }

    /**
     * 동일 행동 판정 키. 지문과 달리 기준일 생략값이 채워지기 전에도 쓸 수 있어야 하므로 추가상환은 생략된 날짜를 빈 값으로 본다
     * (같은 대출의 다른 추가상환은 어차피 충돌이다).
     */
    private static String actionKey(SimulationInput child) {
        if (child instanceof InstallmentInput installment) {
            return installment.actionKey();
        }
        if (child instanceof LoanPrepaymentInput prepayment) {
            return prepayment.loanId() + "|" + prepayment.prepaymentAmount() + "|"
                    + (prepayment.paymentDate() == null ? "" : prepayment.paymentDate());
        }
        return child.fingerprint();
    }

    private static BusinessException conflict() {
        return new BusinessException(ErrorCode.SIMULATION_COMBINED_SCENARIO_CONFLICT);
    }
}
