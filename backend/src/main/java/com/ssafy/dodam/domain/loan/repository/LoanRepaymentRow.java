package com.ssafy.dodam.domain.loan.repository;

import java.time.LocalDate;

/**
 * `loan_repayment` 한 행. 실제 상환 내역이며 향후 상환 일정은 포함하지 않는다.
 */
public record LoanRepaymentRow(
        Long loanRepaymentId,
        LocalDate repaymentDate,
        long amount,
        Long principal,
        Long interest,
        String status,
        String failureReason
) {
}
