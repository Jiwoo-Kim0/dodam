package com.ssafy.dodam.domain.product.dto.response;

import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import java.time.LocalDateTime;
import java.util.List;

public record CardProductDetailResponse(
        Long productId,
        ProductCompanyResponse company,
        String sourceType,
        String externalProductCode,
        String productName,
        String joinWay,
        String specialCondition,
        String joinMember,
        Long baselinePerformance,
        Long maxBenefitLimit,
        String description,
        List<CardBenefitResponse> benefits,
        LocalDateTime syncedAt
) {

    public static CardProductDetailResponse from(FinancialProduct product) {
        return new CardProductDetailResponse(
                product.getId(),
                ProductCompanyResponse.from(product.getCompany()),
                product.getSourceType().name(),
                product.getExternalProductCode(),
                product.getProductName(),
                product.getJoinWay(),
                product.getSpecialCondition(),
                product.getJoinMember(),
                product.getBaselinePerformance(),
                product.getMaxBenefitLimit(),
                product.getDescription(),
                CardBenefitResponse.from(product.getBenefits()),
                product.getSyncedAt()
        );
    }
}
