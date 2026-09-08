package com.ssafy.dodam.domain.product.dto.response;

import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record LoanProductDetailResponse(
        Long productId,
        ProductCompanyResponse company,
        String sourceType,
        String externalProductCode,
        String productName,
        String loanType,
        String joinWay,
        String specialCondition,
        String joinMember,
        BigDecimal minRate,
        BigDecimal maxRate,
        Long maxLimit,
        String description,
        LocalDateTime syncedAt,
        List<ProductOptionResponse> options
) {

    public static LoanProductDetailResponse from(FinancialProduct product) {
        return new LoanProductDetailResponse(
                product.getId(),
                ProductCompanyResponse.from(product.getCompany()),
                product.getSourceType().name(),
                product.getExternalProductCode(),
                product.getProductName(),
                product.getLoanType(),
                product.getJoinWay(),
                product.getSpecialCondition(),
                product.getJoinMember(),
                product.getMinRate(),
                product.getMaxRate(),
                product.getMaxLimit(),
                product.getDescription(),
                product.getSyncedAt(),
                product.getOptions().stream().map(ProductOptionResponse::from).toList()
        );
    }
}
