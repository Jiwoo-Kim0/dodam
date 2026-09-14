package com.ssafy.dodam.domain.recommendation.dto.response;

import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationHistory;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationStatus;
import java.time.LocalDateTime;

/** 추천 이력 1건(GET /history). 생성 시 남긴 PENDING 이력도 포함된다. */
public record RecommendationHistoryResponse(
        Long historyId,
        Long recommendationId,
        String title,
        String domain,
        RecommendationStatus status,
        String reason,
        String memo,
        LocalDateTime changedAt
) {
    public static RecommendationHistoryResponse of(RecommendationHistory h, Recommendation r) {
        return new RecommendationHistoryResponse(h.getRecommendationHistoryId(), h.getRecommendationId(),
                r == null ? null : r.getTitle(), r == null ? null : r.getDomain(), h.getStatus(), h.getReason(), h.getMemo(),
                h.getChangedAt());
    }
}
