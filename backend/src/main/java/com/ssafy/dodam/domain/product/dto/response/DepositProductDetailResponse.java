package com.ssafy.dodam.domain.product.dto.response;

import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DepositProductDetailResponse(
        Long productId,
        ProductCompanyResponse company,
        String sourceType,
        String externalProductCode,
        String productName,
        String joinWay,
        String specialCondition,
        String joinMember,
        BigDecimal minRate,
        BigDecimal maxRate,
        String description,
        LocalDateTime syncedAt,
        List<ProductOptionResponse> options
) {

    public static DepositProductDetailResponse from(FinancialProduct product) {
        return new DepositProductDetailResponse(
                product.getId(),
                ProductCompanyResponse.from(product.getCompany()),
                product.getSourceType().name(),
                product.getExternalProductCode(),
                product.getProductName(),
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
