package com.ssafy.dodam.domain.loan.service;

import java.time.LocalDate;

/**
 * 계산된 상환 회차. 금액은 모두 원 단위 정수다.
 */
public record LoanRepaymentScheduleItem(
        int sequence,
        LocalDate repaymentDate,
        long amount,
        long principal,
        long interest,
        long remainingBalance
) {
}
