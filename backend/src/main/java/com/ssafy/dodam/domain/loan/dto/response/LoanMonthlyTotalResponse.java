package com.ssafy.dodam.domain.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record LoanMonthlyTotalResponse(
        boolean complete,
        List<String> unavailableReasons,
        Long monthlyRepaymentAmount
) {
}
