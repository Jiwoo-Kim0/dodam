package com.ssafy.dodam.domain.product.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record RecommendedCardResponse(
        int rank,
        Long productId,
        ProductCompanyResponse company,
        String productName,
        Long baselinePerformance,
        boolean estimatedPerformanceReachable,
        long performanceShortfall,
        long estimatedMonthlyBenefit,
        long potentialMonthlyBenefit,
        Long maxBenefitLimit,
        BigDecimal benefitCoverageRate,
        List<RecommendedBenefitResponse> matchedBenefits,
        String reason
) {

    public RecommendedCardResponse {
        matchedBenefits = List.copyOf(matchedBenefits);
    }

    public RecommendedCardResponse withRank(int assignedRank) {
        return new RecommendedCardResponse(
                assignedRank,
                productId,
                company,
                productName,
                baselinePerformance,
                estimatedPerformanceReachable,
                performanceShortfall,
                estimatedMonthlyBenefit,
                potentialMonthlyBenefit,
                maxBenefitLimit,
                benefitCoverageRate,
                matchedBenefits,
                reason
        );
    }
}
