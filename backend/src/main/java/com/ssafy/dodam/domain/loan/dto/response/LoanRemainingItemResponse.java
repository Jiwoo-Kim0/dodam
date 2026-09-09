package com.ssafy.dodam.domain.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record LoanRemainingItemResponse(
        Long loanId,
        String accountName,
        String accountNoMasked,
        String status,
        List<String> reasons,
        Long principal,
        Long interest,
        Long repaymentAmount
) {
}
