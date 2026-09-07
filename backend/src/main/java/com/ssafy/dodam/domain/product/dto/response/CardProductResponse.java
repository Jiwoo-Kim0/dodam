package com.ssafy.dodam.domain.product.dto.response;

import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import java.time.LocalDateTime;
import java.util.List;

public record CardProductResponse(
        Long productId,
        ProductCompanyResponse company,
        String productName,
        Long baselinePerformance,
        Long maxBenefitLimit,
        List<CardBenefitResponse> benefits,
        LocalDateTime syncedAt
) {

    public static CardProductResponse from(FinancialProduct product) {
        return new CardProductResponse(
                product.getId(),
                ProductCompanyResponse.from(product.getCompany()),
                product.getProductName(),
                product.getBaselinePerformance(),
                product.getMaxBenefitLimit(),
                CardBenefitResponse.from(product.getBenefits()),
                product.getSyncedAt()
        );
    }
}
