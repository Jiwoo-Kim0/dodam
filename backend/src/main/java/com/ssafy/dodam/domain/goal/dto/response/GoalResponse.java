package com.ssafy.dodam.domain.goal.dto.response;

import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.SavingGoal;
import com.ssafy.dodam.domain.goal.entity.SpendingGoal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 목표 한 건. 목록·상세·생성·수정 응답이 같은 형태다.
 *
 * <p>유형별 필드는 해당 유형에서만 값이 있다.
 * <ul>
 *   <li>SAVING: `targetAmount`, `targetDate`, `remainingAmount`, `progressRatePercent`, `daysRemaining`</li>
 *   <li>SPENDING: `monthlyBudget`. 이번 달 소비 대비 진행률은 `GET /goals/{goalId}/spending-progress`(510)에서</li>
 * </ul>
 *
 * @param currentAmount       모으기 목표에 모인 금액(연결 계좌 잔액 합, 509에서 갱신). 줄이기 목표는 0
 * @param progressRatePercent currentAmount / targetAmount (%), 100을 넘지 않는다
 * @param daysRemaining       기준일부터 목표일까지 남은 일수. 지났으면 음수
 */
public record GoalResponse(
        Long goalId,
        String name,
        String type,
        String status,
        long currentAmount,
        Long targetAmount,
        LocalDate targetDate,
        Long remainingAmount,
        Double progressRatePercent,
        Long daysRemaining,
        Long monthlyBudget,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static GoalResponse from(Goal goal, SavingGoal savingGoal, SpendingGoal spendingGoal, LocalDate today) {
        Long targetAmount = savingGoal == null ? null : savingGoal.getTargetAmount();
        LocalDate targetDate = savingGoal == null ? null : savingGoal.getTargetDate();
        return new GoalResponse(
                goal.getId(),
                goal.getName(),
                goal.getType().name(),
                goal.getStatus().name(),
                goal.getCurrentAmount(),
                targetAmount,
                targetDate,
                targetAmount == null ? null : Math.max(0L, targetAmount - goal.getCurrentAmount()),
                targetAmount == null ? null : progress(goal.getCurrentAmount(), targetAmount),
                targetDate == null ? null : ChronoUnit.DAYS.between(today, targetDate),
                spendingGoal == null ? null : spendingGoal.getMonthlyBudget(),
                goal.getCreatedAt(),
                goal.getUpdatedAt()
        );
    }

    private static double progress(long current, long target) {
        if (target <= 0) {
            return 0.0;
        }
        double rate = BigDecimal.valueOf(current)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(target), 1, RoundingMode.HALF_UP)
                .doubleValue();
        return Math.min(100.0, rate);
    }
}
