package com.ssafy.dodam.domain.loan.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 대출 요약. 잔액이 남은 대출(ACTIVE, OVERDUE)만 집계한다.
 *
 * <p>`weightedAverageRate`는 단순 평균이 아니라 잔액 가중평균이다. 소액 고금리 대출이 평균을
 * 왜곡하면 고금리 판단 기준과 어긋나기 때문이다. 집계 대상이 없으면 null이다.
 */
public record LoanSummaryResponse(
        long loanCount,
        long totalOriginalPrincipal,
        long totalBalance,
        long monthlyRepayment,
        BigDecimal weightedAverageRate,
        LocalDate nextRepaymentDate,
        List<LoanTypeSummaryResponse> loanTypes
) {

    public LoanSummaryResponse {
        loanTypes = List.copyOf(loanTypes);
    }
}
