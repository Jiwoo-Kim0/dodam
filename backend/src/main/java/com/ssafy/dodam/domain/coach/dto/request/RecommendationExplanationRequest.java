package com.ssafy.dodam.domain.coach.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** POST /api/v1/coach/recommendation-explanation 본문. 명세 21: {@code recommendationId}. */
public record RecommendationExplanationRequest(@NotNull @Positive Long recommendationId) {
}
