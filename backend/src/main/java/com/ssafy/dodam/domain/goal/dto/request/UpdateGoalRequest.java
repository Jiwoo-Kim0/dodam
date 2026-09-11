package com.ssafy.dodam.domain.goal.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * PATCH /api/v1/goals/{goalId} — 목표 수정. null(생략)은 "변경 없음"이다. 모든 필드가 비어 있으면 400 NO_FIELDS_TO_UPDATE.
 *
 * <ul>
 *   <li>`targetAmount`·`targetDate`는 SAVING 목표만, `monthlyBudget`은 SPENDING 목표만 받는다</li>
 *   <li>`status`는 ACTIVE / PAUSED / ACHIEVED / CANCELED. 허용된 전이만 가능하다</li>
 *   <li>종료 상태(ACHIEVED·CANCELED) 목표는 어떤 필드도 바꿀 수 없다</li>
 * </ul>
 */
public record UpdateGoalRequest(
        @Size(min = 1, max = 150, message = "목표 이름은 1~150자여야 합니다.")
        String name,
        @Positive(message = "목표 금액은 1 이상이어야 합니다.")
        Long targetAmount,
        LocalDate targetDate,
        @Positive(message = "월 예산은 1 이상이어야 합니다.")
        Long monthlyBudget,
        @Size(max = 30)
        String status
) {

    public boolean isEmpty() {
        return name == null && targetAmount == null && targetDate == null && monthlyBudget == null && status == null;
    }
}
