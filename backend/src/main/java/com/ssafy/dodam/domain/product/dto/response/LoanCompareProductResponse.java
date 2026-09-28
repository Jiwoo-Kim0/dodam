package com.ssafy.dodam.domain.product.dto.response;

import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record LoanCompareProductResponse(
        Long productId,
        ProductCompanyResponse company,
        String productName,
        String loanType,
        String joinWay,
        String specialCondition,
        String joinMember,
        BigDecimal minRate,
        BigDecimal maxRate,
        Long maxLimit,
        List<ProductOptionResponse> options,
        LocalDateTime syncedAt
) {

    public static LoanCompareProductResponse from(FinancialProduct product) {
        return new LoanCompareProductResponse(
                product.getId(),
                ProductCompanyResponse.from(product.getCompany()),
                product.getProductName(),
                product.getLoanType(),
                product.getJoinWay(),
                product.getSpecialCondition(),
                product.getJoinMember(),
                product.getMinRate(),
                product.getMaxRate(),
                product.getMaxLimit(),
                product.getOptions().stream().map(ProductOptionResponse::from).toList(),
                product.getSyncedAt()
        );
    }
}
