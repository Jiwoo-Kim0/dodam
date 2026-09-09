package com.ssafy.dodam.domain.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record LoanCostItemResponse(
        Long loanId,
        String accountName,
        String accountNoMasked,
        String institutionName,
        Long balance,
        BigDecimal interestRate,
        Long forecastInterest,
        BigDecimal interestShare,
        String status,
        List<String> reasons
) {
}
