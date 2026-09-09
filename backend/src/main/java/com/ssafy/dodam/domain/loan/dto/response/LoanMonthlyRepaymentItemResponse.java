package com.ssafy.dodam.domain.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.time.YearMonth;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record LoanMonthlyRepaymentItemResponse(
        YearMonth yearMonth,
        LoanMonthlyTotalResponse monthlyTotal,
        LoanRemainingAmountsResponse remaining,
        List<LoanRemainingItemResponse> loans
) {
}
