package com.ssafy.dodam.domain.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.time.YearMonth;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record LoanInterestMonthItemResponse(
        YearMonth yearMonth,
        boolean complete,
        List<String> unavailableReasons,
        List<LoanUnavailableResponse> unavailableLoans,
        Long knownInterest,
        Long totalInterest
) {
}
