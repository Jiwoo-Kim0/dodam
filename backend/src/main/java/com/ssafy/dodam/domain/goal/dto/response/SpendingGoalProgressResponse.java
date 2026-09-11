package com.ssafy.dodam.domain.goal.dto.response;

import java.time.LocalDate;

/**
 * 소비목표 진행률(GET /goals/{goalId}/spending-progress). 줄이기 목표 전용. 기획안 「줄이기 목표」의 월 소비 목표·누적 소비·남은 소비 가능 금액.
 *
 * @param spent                  이번 달 소비(506 정의, 오늘까지)
 * @param remainingBudget        monthlyBudget − spent. 음수면 초과액
 * @param usageRatePercent       spent / monthlyBudget (%)
 * @param dailyBudgetRemaining   남은 예산 / 남은 일수(오늘 제외). 남은 일이 없으면 null, 초과면 0
 * @param projectedMonthEnd      현재 소비속도 기준 예상 월 소비(spent × 그 달 일수 / 경과 일수)
 * @param previousMonthSpending  전월 소비
 * @param expectedSavingAmount   전월 대비 예상 절감액 = max(0, previousMonthSpending − projectedMonthEnd)
 * @param status                 ON_TRACK(80% 미만) / WARNING(80~100%) / EXCEEDED(100% 초과)
 */
public record SpendingGoalProgressResponse(
        Long goalId,
        String goalStatus,
        String yearMonth,
        LocalDate asOfDate,
        int daysElapsed,
        int daysInMonth,
        int daysLeft,
        long monthlyBudget,
        long spent,
        long remainingBudget,
        double usageRatePercent,
        Long dailyBudgetRemaining,
        long projectedMonthEnd,
        long previousMonthSpending,
        long expectedSavingAmount,
        String status
) {
}
