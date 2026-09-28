package com.ssafy.dodam.domain.goal.dto.response;

import java.util.List;

/**
 * 목표 위험상태(GET /goals/{goalId}/risk). 기획안 「모으기 목표 달성 위험」·「줄이기 목표 초과 위험」 규칙 기반 판정.
 *
 * @param riskLevel  NONE(종료 상태·달성) / LOW / MEDIUM / HIGH
 * @param factors    판정 근거 코드. 모으기: DEADLINE_PASSED, NO_LINKED_ACCOUNTS, PACE_INSUFFICIENT, BEHIND_SCHEDULE.
 *                   줄이기: OVER_BUDGET, PACE_EXCEEDS_BUDGET. 비어 있으면 위험 요인 없음
 * @param metrics    판정에 쓴 수치. 모으기: requiredMonthlySaving·averageMonthlySaving·progressRatePercent·expectedProgressRatePercent.
 *                   줄이기: monthlyBudget·spent·projectedMonthEnd·usageRatePercent
 */
public record GoalRiskResponse(
        Long goalId,
        String type,
        String goalStatus,
        String riskLevel,
        List<String> factors,
        Metrics metrics
) {

    public GoalRiskResponse {
        factors = List.copyOf(factors);
    }

    public record Metrics(
            Long requiredMonthlySaving,
            Long averageMonthlySaving,
            Double progressRatePercent,
            Double expectedProgressRatePercent,
            Long monthlyBudget,
            Long spent,
            Long projectedMonthEnd,
            Double usageRatePercent
    ) {

        public static Metrics saving(long required, long average, double progress, double expected) {
            return new Metrics(required, average, progress, expected, null, null, null, null);
        }

        public static Metrics spending(long budget, long spent, long projected, double usage) {
            return new Metrics(null, null, null, null, budget, spent, projected, usage);
        }
    }
}
