package com.ssafy.dodam.domain.loan.dto.response;

import com.ssafy.dodam.domain.loan.repository.LoanRepaymentRow;
import java.time.LocalDate;

/**
 * 실제 상환 내역 한 건.
 */
public record LoanRepaymentResponse(
        Long repaymentId,
        LocalDate repaymentDate,
        long amount,
        Long principal,
        Long interest,
        String status,
        String failureReason
) {

    public static LoanRepaymentResponse from(LoanRepaymentRow row) {
        return new LoanRepaymentResponse(
                row.loanRepaymentId(),
                row.repaymentDate(),
                row.amount(),
                row.principal(),
                row.interest(),
                row.status(),
                row.failureReason()
        );
    }
}
