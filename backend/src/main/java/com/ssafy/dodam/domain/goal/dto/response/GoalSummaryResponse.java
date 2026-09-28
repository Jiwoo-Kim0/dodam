package com.ssafy.dodam.domain.goal.dto.response;

/**
 * 목표 요약(GET /goals/summary). 삭제된 목표는 어디에도 들어가지 않는다.
 *
 * @param totalCount                  전체 목표 수(상태 무관)
 * @param activeCount                 ACTIVE 목표 수
 * @param pausedCount                 PAUSED 목표 수
 * @param achievedCount               ACHIEVED 목표 수
 * @param canceledCount               CANCELED 목표 수
 * @param savingGoalCount             모으기 목표 수(상태 무관)
 * @param activeSavingTargetAmount    ACTIVE 모으기 목표의 목표 금액 합
 * @param activeSavingCurrentAmount   ACTIVE 모으기 목표에 모인 금액 합
 * @param activeSavingProgressPercent 위 두 값의 비율(%), 목표 금액이 0이면 0
 * @param spendingGoalCount           줄이기 목표 수(상태 무관)
 * @param activeMonthlyBudget         ACTIVE 줄이기 목표의 월 예산 합
 */
public record GoalSummaryResponse(
        long totalCount,
        long activeCount,
        long pausedCount,
        long achievedCount,
        long canceledCount,
        long savingGoalCount,
        long activeSavingTargetAmount,
        long activeSavingCurrentAmount,
        double activeSavingProgressPercent,
        long spendingGoalCount,
        long activeMonthlyBudget
) {
}
