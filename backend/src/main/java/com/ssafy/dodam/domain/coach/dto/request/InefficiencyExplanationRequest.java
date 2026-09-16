package com.ssafy.dodam.domain.coach.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** POST /api/v1/coach/inefficiency-explanation 본문. 명세 21: {@code diagnosisId, issueId}. */
public record InefficiencyExplanationRequest(
        @NotNull @Positive Long diagnosisId,
        @NotNull @Positive Long issueId
) {
}
