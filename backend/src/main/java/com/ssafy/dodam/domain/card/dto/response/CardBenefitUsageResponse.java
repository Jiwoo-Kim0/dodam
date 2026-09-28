package com.ssafy.dodam.domain.card.dto.response;

import java.math.BigDecimal;

public record CardBenefitUsageResponse(
        String cardNoMasked,
        String yearMonth,
        boolean benefitDataAvailable,
        long totalSpending,
        Long matchedSpending,
        BigDecimal coverageRate,
        Long baselinePerformance,
        Boolean performanceMet,
        Long performanceShortfall,
        Long estimatedBenefit,
        Long maxBenefitLimit,
        BigDecimal benefitLimitUsageRate
) {
}
