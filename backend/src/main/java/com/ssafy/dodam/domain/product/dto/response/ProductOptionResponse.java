package com.ssafy.dodam.domain.product.dto.response;

import com.ssafy.dodam.domain.product.entity.FinancialProductOption;
import java.math.BigDecimal;

public record ProductOptionResponse(
        Long productOptionId,
        Integer term,
        BigDecimal rate,
        BigDecimal preferredRate,
        String rateType,
        BigDecimal minRate,
        BigDecimal maxRate,
        String repaymentType,
        Long limitAmount
) {

    public static ProductOptionResponse from(FinancialProductOption option) {
        return new ProductOptionResponse(
                option.getId(),
                option.getTerm(),
                option.getRate(),
                option.getPreferredRate(),
                option.getRateType(),
                option.getMinRate(),
                option.getMaxRate(),
                option.getRepaymentType(),
                option.getLimitAmount()
        );
    }
}
