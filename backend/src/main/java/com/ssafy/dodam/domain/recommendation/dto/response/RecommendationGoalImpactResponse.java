package com.ssafy.dodam.domain.recommendation.dto.response;

import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import java.time.LocalDate;

/**
 * 목표 기여도(GET /{id}/goal-impact).
 *
 * @param unavailableReason 값이 없는 이유. {@code NO_GOAL}: 목표 없는 추천, {@code NOT_CALCULATED}: 목표는 있으나 생성기가
 *                          날짜·기여 효과를 만들지 않음. 값이 있으면 null
 */
public record RecommendationGoalImpactResponse(
        Long recommendationId,
        Long goalId,
        LocalDate currentForecastDate,
        LocalDate improvedForecastDate,
        Integer advancedDays,
        Long contributionAmount,
        String unavailableReason
) {
    public static final String NO_GOAL = "NO_GOAL";
    public static final String NOT_CALCULATED = "NOT_CALCULATED";

    public static RecommendationGoalImpactResponse from(Recommendation r) {
        String reason = null;
        if (r.getGoalId() == null) {
            reason = NO_GOAL;
        } else if (r.getCurrentForecastDate() == null && r.getImprovedForecastDate() == null && r.getContributionAmount() == null) {
            reason = NOT_CALCULATED;
        }
        return new RecommendationGoalImpactResponse(r.getRecommendationId(), r.getGoalId(), r.getCurrentForecastDate(),
                r.getImprovedForecastDate(), r.getAdvancedDays(), r.getContributionAmount(), reason);
    }
}
