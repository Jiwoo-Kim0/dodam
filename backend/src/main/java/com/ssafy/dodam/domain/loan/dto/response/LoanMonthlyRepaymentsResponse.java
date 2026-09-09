package com.ssafy.dodam.domain.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.time.LocalDate;
import java.time.YearMonth;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record LoanMonthlyRepaymentsResponse(
        LocalDate asOfDate,
        List<String> assumptions,
        boolean complete,
        List<String> unavailableReasons,
        List<LoanUnavailableResponse> unavailableLoans,
        String calculationBasis,
        YearMonth startMonth,
        YearMonth endMonth,
        List<LoanMonthlyRepaymentItemResponse> months
) {
}
