package com.ssafy.dodam.domain.recommendation.dto.response;

import java.util.Map;

/**
 * 개선안 요약(GET /summary).
 *
 * @param byStatus                   5개 상태 모두 포함(0 포함)
 * @param byDomain                   도메인별 건수. 추천이 없으면 빈 객체
 * @param expectedMonthlyCostSaving  PENDING·ACCEPTED 추천의 monthlyCostSaving 합(null 제외)
 * @param expectedContributionAmount PENDING·ACCEPTED 추천의 contributionAmount 합(null 제외)
 */
public record RecommendationSummaryResponse(
        long total,
        Map<String, Long> byStatus,
        Map<String, Long> byDomain,
        long expectedMonthlyCostSaving,
        long expectedContributionAmount
) {
}
