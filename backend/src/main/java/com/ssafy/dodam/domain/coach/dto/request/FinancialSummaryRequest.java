package com.ssafy.dodam.domain.coach.dto.request;

import jakarta.validation.constraints.Positive;

/**
 * POST /api/v1/coach/financial-summary 본문. 명세 21: {@code diagnosisId?}.
 *
 * <p>비우면 사용자의 최신 완료 진단을 쓴다. 본문 자체를 생략해도 된다.
 */
public record FinancialSummaryRequest(@Positive Long diagnosisId) {
}
