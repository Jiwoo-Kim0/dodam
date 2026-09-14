package com.ssafy.dodam.domain.recommendation.dto.response;

import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationStatus;
import java.time.LocalDateTime;

/** 개선안 1건. 효과는 저장값 그대로이며 현재 데이터로 재계산하지 않는다. */
public record RecommendationResponse(
        Long recommendationId,
        Long diagnosisId,
        Long goalId,
        String domain,
        String type,
        String title,
        String description,
        int priority,
        RecommendationStatus status,
        RecommendationEffectResponse effect,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static RecommendationResponse from(Recommendation r) {
        return new RecommendationResponse(r.getRecommendationId(), r.getDiagnosisId(), r.getGoalId(), r.getDomain(), r.getType(),
                r.getTitle(), r.getDescription(), r.getPriority(), r.getStatus(), RecommendationEffectResponse.from(r),
                r.getCreatedAt(), r.getUpdatedAt());
    }
}
