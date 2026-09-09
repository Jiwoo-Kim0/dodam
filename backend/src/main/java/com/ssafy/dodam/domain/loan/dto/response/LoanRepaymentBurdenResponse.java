package com.ssafy.dodam.domain.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.time.LocalDate;
import java.time.YearMonth;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record LoanRepaymentBurdenResponse(
        LocalDate asOfDate,
        List<String> assumptions,
        boolean complete,
        List<String> unavailableReasons,
        List<LoanUnavailableResponse> unavailableLoans,
        String calculationBasis,
        YearMonth yearMonth,
        Long monthlyIncome,
        String incomeBasis,
        LoanMonthlyTotalResponse monthlyTotal,
        LoanRemainingAmountsResponse remaining,
        BigDecimal burdenRate
) {
}
