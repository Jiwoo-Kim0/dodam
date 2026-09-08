package com.ssafy.dodam.domain.product.dto.response;

import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LoanProductResponse(
        Long productId,
        ProductCompanyResponse company,
        String productName,
        String loanType,
        BigDecimal minRate,
        BigDecimal maxRate,
        Long maxLimit,
        LocalDateTime syncedAt
) {

    public static LoanProductResponse from(FinancialProduct product) {
        return new LoanProductResponse(
                product.getId(),
                ProductCompanyResponse.from(product.getCompany()),
                product.getProductName(),
                product.getLoanType(),
                product.getMinRate(),
                product.getMaxRate(),
                product.getMaxLimit(),
                product.getSyncedAt()
        );
    }
}
