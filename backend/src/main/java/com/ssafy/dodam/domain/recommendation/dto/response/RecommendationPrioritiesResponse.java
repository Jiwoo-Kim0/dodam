package com.ssafy.dodam.domain.recommendation.dto.response;

import java.util.List;

/**
 * 우선순위 조회(GET /priorities). 최신 완료 진단의 PENDING·DEFERRED 추천을 priority 오름차순으로.
 *
 * @param diagnosisId 기준이 된 진단. 완료 진단이 없으면 null이고 목록도 비어 있다
 */
public record RecommendationPrioritiesResponse(Long diagnosisId, Long goalId, List<RecommendationResponse> recommendations) {
    public RecommendationPrioritiesResponse {
        recommendations = List.copyOf(recommendations);
    }
}
