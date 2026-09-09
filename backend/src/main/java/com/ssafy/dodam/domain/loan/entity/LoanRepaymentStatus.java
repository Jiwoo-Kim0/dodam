package com.ssafy.dodam.domain.loan.entity;

/**
 * ERD `loan_repayment.status` CHECK 제약과 동일한 상환 내역 상태 값.
 */
public enum LoanRepaymentStatus {
    SCHEDULED,
    PAID,
    FAILED,
    OVERDUE
}
