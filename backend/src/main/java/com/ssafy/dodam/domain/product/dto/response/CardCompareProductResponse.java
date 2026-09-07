package com.ssafy.dodam.domain.product.dto.response;

import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import java.time.LocalDateTime;
import java.util.List;

public record CardCompareProductResponse(
        Long productId,
        ProductCompanyResponse company,
        String productName,
        String joinWay,
        String specialCondition,
        String joinMember,
        Long baselinePerformance,
        Long maxBenefitLimit,
        List<CardBenefitResponse> benefits,
        LocalDateTime syncedAt
) {

    public static CardCompareProductResponse from(FinancialProduct product) {
        return new CardCompareProductResponse(
                product.getId(),
                ProductCompanyResponse.from(product.getCompany()),
                product.getProductName(),
                product.getJoinWay(),
                product.getSpecialCondition(),
                product.getJoinMember(),
                product.getBaselinePerformance(),
                product.getMaxBenefitLimit(),
                CardBenefitResponse.from(product.getBenefits()),
                product.getSyncedAt()
        );
    }
}
