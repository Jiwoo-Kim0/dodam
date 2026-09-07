package com.ssafy.dodam.domain.product.dto.response;

import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DepositSavingsCompareProductResponse(
        Long productId,
        String productType,
        ProductCompanyResponse company,
        String productName,
        String savingType,
        String joinWay,
        String specialCondition,
        String joinMember,
        BigDecimal minRate,
        BigDecimal maxRate,
        List<ProductOptionResponse> options,
        LocalDateTime syncedAt
) {

    public static DepositSavingsCompareProductResponse from(FinancialProduct product) {
        return new DepositSavingsCompareProductResponse(
                product.getId(),
                product.getProductType().name(),
                ProductCompanyResponse.from(product.getCompany()),
                product.getProductName(),
                product.getSavingType(),
                product.getJoinWay(),
                product.getSpecialCondition(),
                product.getJoinMember(),
                product.getMinRate(),
                product.getMaxRate(),
                product.getOptions().stream().map(ProductOptionResponse::from).toList(),
                product.getSyncedAt()
        );
    }
}
