package com.ssafy.dodam.domain.product.dto.response;

import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SavingsProductResponse(
        Long productId,
        ProductCompanyResponse company,
        String productName,
        String savingType,
        String joinWay,
        BigDecimal minRate,
        BigDecimal maxRate,
        LocalDateTime syncedAt
) {

    public static SavingsProductResponse from(FinancialProduct product) {
        return new SavingsProductResponse(
                product.getId(),
                ProductCompanyResponse.from(product.getCompany()),
                product.getProductName(),
                product.getSavingType(),
                product.getJoinWay(),
                product.getMinRate(),
                product.getMaxRate(),
                product.getSyncedAt()
        );
    }
}
