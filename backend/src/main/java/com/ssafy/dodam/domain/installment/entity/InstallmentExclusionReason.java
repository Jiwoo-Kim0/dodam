package com.ssafy.dodam.domain.installment.entity;

public enum InstallmentExclusionReason {
    OVERDUE,
    NO_REMAINING_PAYMENTS,
    MISSING_NEXT_PAYMENT_DATE,
    INCONSISTENT_PAYMENT_DATES,
    STALE_NEXT_PAYMENT_DATE
}
