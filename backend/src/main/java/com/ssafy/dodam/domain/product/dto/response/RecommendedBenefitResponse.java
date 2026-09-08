package com.ssafy.dodam.domain.product.dto.response;

import java.math.BigDecimal;

public record RecommendedBenefitResponse(
        String category,
        long spendingAmount,
        BigDecimal discountRate,
        long expectedBenefit,
        boolean calculable,
        String description
) {
}
