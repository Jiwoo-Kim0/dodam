package com.ssafy.dodam.domain.transaction.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 소비목표 비교(GET /spending/goal-comparison). `spending_goal.monthly_budget`(월 예산)과 그 달 소비(506 정의)를 비교한다.
 * 소비목표에는 카테고리가 없으므로 예산은 월 전체 소비에 대한 것이다.
 *
 * @param asOfDate    기준일. 이번 달이면 오늘, 지난 달이면 말일
 * @param spending    그 달 소비 합(모든 목표에 공통)
 * @param goals       `goalId`를 주면 1건, 없으면 ACTIVE 소비목표 전부(목표 ID 순)
 */
public record SpendingGoalComparisonResponse(
        String yearMonth,
        LocalDate asOfDate,
        int daysElapsed,
        int daysInMonth,
        long spending,
        List<Goal> goals
) {

    public SpendingGoalComparisonResponse {
        goals = List.copyOf(goals);
    }

    /**
     * @param remaining            monthlyBudget − spending. 음수면 초과액
     * @param usageRatePercent     spending / monthlyBudget (%)
     * @param projectedMonthEnd    현재 추세로 월말까지 쓸 금액(총액 × 그 달 일수 / 경과 일수). 지난 달은 spending과 같다
     * @param dailyBudgetRemaining 남은 예산 / 남은 일수(오늘 제외). 이번 달이 아니거나 남은 일이 없으면 null, 초과했으면 0
     * @param status               ON_TRACK(80% 미만) / WARNING(80% 이상 100% 이하) / EXCEEDED(100% 초과)
     */
    public record Goal(
            Long goalId,
            String name,
            String goalStatus,
            long monthlyBudget,
            long remaining,
            double usageRatePercent,
            long projectedMonthEnd,
            Long dailyBudgetRemaining,
            String status
    ) {
    }
}
