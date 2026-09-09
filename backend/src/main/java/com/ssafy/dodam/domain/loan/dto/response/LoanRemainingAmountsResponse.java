package com.ssafy.dodam.domain.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record LoanRemainingAmountsResponse(
        boolean complete,
        List<String> unavailableReasons,
        List<LoanUnavailableResponse> unavailableLoans,
        Long knownPrincipal,
        Long knownInterest,
        Long knownRepaymentAmount,
        Long totalPrincipal,
        Long totalInterest,
        Long remainingRepaymentAmount
) {
}
