package com.ssafy.dodam.domain.loan.dto.response;

import com.ssafy.dodam.domain.loan.service.LoanRepaymentScheduleItem;
import java.time.LocalDate;

/**
 * 향후 상환 회차. 저장값이 아니라 계산값이다.
 */
public record LoanRepaymentScheduleItemResponse(
        int sequence,
        LocalDate repaymentDate,
        long amount,
        long principal,
        long interest,
        long remainingBalance
) {

    public static LoanRepaymentScheduleItemResponse from(LoanRepaymentScheduleItem item) {
        return new LoanRepaymentScheduleItemResponse(
                item.sequence(),
                item.repaymentDate(),
                item.amount(),
                item.principal(),
                item.interest(),
                item.remainingBalance()
        );
    }
}
