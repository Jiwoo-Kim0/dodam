package com.ssafy.dodam.domain.product.dto.response;

import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SavingsProductDetailResponse(
        Long productId,
        ProductCompanyResponse company,
        String sourceType,
        String externalProductCode,
        String productName,
        String savingType,
        String joinWay,
        String specialCondition,
        String joinMember,
        BigDecimal minRate,
        BigDecimal maxRate,
        String description,
        LocalDateTime syncedAt,
        List<ProductOptionResponse> options
) {

    public static SavingsProductDetailResponse from(FinancialProduct product) {
        return new SavingsProductDetailResponse(
                product.getId(),
                ProductCompanyResponse.from(product.getCompany()),
                product.getSourceType().name(),
                product.getExternalProductCode(),
                product.getProductName(),
                product.getSavingType(),
                product.getJoinWay(),
                product.getSpecialCondition(),
                product.getJoinMember(),
                product.getMinRate(),
                product.getMaxRate(),
                product.getDescription(),
                product.getSyncedAt(),
                product.getOptions().stream().map(ProductOptionResponse::from).toList()
        );
    }
}
