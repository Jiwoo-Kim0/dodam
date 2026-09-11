package com.ssafy.dodam.domain.goal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * POST /api/v1/goals — 목표 생성.
 *
 * <p>유형별 필수 항목은 서비스가 검증한다.
 * <ul>
 *   <li>SAVING(모으기): `targetAmount`, `targetDate`(오늘 이후) 필수, `monthlyBudget`은 받지 않는다</li>
 *   <li>SPENDING(줄이기): `monthlyBudget` 필수, `targetAmount`·`targetDate`는 받지 않는다(ERD `spending_goal`에 저장 컬럼이 없다)</li>
 * </ul>
 */
public record CreateGoalRequest(
        @NotBlank(message = "목표 이름은 필수입니다.")
        @Size(max = 150, message = "목표 이름은 150자 이하여야 합니다.")
        String name,
        @NotBlank(message = "목표 유형은 필수입니다.")
        @Size(max = 20)
        String type,
        @Positive(message = "목표 금액은 1 이상이어야 합니다.")
        Long targetAmount,
        LocalDate targetDate,
        @Positive(message = "월 예산은 1 이상이어야 합니다.")
        Long monthlyBudget
) {
}
