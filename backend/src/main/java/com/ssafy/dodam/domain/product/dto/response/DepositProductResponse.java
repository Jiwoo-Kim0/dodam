package com.ssafy.dodam.domain.product.dto.response;

import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DepositProductResponse(
        Long productId,
        ProductCompanyResponse company,
        String productName,
        String joinWay,
        BigDecimal minRate,
        BigDecimal maxRate,
        LocalDateTime syncedAt
) {

    public static DepositProductResponse from(FinancialProduct product) {
        return new DepositProductResponse(
                product.getId(),
                ProductCompanyResponse.from(product.getCompany()),
                product.getProductName(),
                product.getJoinWay(),
                product.getMinRate(),
                product.getMaxRate(),
                product.getSyncedAt()
        );
    }
}
