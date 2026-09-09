package com.ssafy.dodam.domain.loan.entity;

/**
 * ERD `loan.status` CHECK 제약과 동일한 대출 상태 값.
 */
public enum LoanStatus {
    ACTIVE,
    OVERDUE,
    COMPLETED,
    CLOSED;

    /**
     * 잔액이 남아 상환이 진행 중인 상태. 요약과 상환일정 계산의 기본 대상이다.
     */
    public boolean isOutstanding() {
        return this == ACTIVE || this == OVERDUE;
    }
}
