package com.ssafy.dodam.domain.loan.dto.response;

/**
 * 대출 유형별 집계. 기획안의 "대출유형" 항목에 대응한다.
 */
public record LoanTypeSummaryResponse(
        String loanType,
        long loanCount,
        long balance
) {
}
