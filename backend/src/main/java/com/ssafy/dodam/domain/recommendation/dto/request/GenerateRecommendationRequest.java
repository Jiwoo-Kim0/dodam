package com.ssafy.dodam.domain.recommendation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** POST /api/v1/recommendations/generate 본문. 명세 16: {@code diagnosisId, goalId?}. */
public record GenerateRecommendationRequest(@NotNull @Positive Long diagnosisId, @Positive Long goalId) {
}
