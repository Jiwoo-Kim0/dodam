package com.ssafy.dodam.domain.recommendation.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationReason;

public record RecommendationReasonResponse(Long reasonId, String code, String description, JsonNode evidence) {
    public static RecommendationReasonResponse from(RecommendationReason reason) {
        return new RecommendationReasonResponse(reason.getReasonId(), reason.getCode(), reason.getDescription(), reason.getEvidence());
    }
}
