package com.ssafy.dodam.domain.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * API 명세: 월소득?, 연소득?, 급여일?, 고정지출?, 비상자금 기준?. 모두 선택이며 null인 항목은 변경하지 않는다.
 * 금액 단위는 원(KRW) 정수. 제약은 ERD CHECK와 동일하다.
 */
public record UpdateFinancialProfileRequest(
        @PositiveOrZero(message = "monthlyIncome은 0 이상이어야 합니다.")
        Long monthlyIncome,

        @PositiveOrZero(message = "annualIncome은 0 이상이어야 합니다.")
        Long annualIncome,

        @Min(value = 1, message = "payday는 1 이상이어야 합니다.")
        @Max(value = 31, message = "payday는 31 이하여야 합니다.")
        Integer payday,

        @PositiveOrZero(message = "fixedExpense는 0 이상이어야 합니다.")
        Long fixedExpense,

        @PositiveOrZero(message = "emergencyFundTarget은 0 이상이어야 합니다.")
        Long emergencyFundTarget
) {

    public boolean isEmpty() {
        return monthlyIncome == null
                && annualIncome == null
                && payday == null
                && fixedExpense == null
                && emergencyFundTarget == null;
    }
}
