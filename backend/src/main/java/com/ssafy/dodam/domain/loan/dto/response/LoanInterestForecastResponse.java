package com.ssafy.dodam.domain.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record LoanInterestForecastResponse(
        LocalDate asOfDate,
        List<String> assumptions,
        String calculationBasis,
        Long loanId,
        String accountNoMasked,
        String status,
        List<String> reasons,
        LocalDate maturityDate,
        LocalDate coveredThrough,
        boolean truncated,
        Long knownInterest,
        Long fullTermInterest,
        List<LoanInterestMonthItemResponse> months
) {
}
