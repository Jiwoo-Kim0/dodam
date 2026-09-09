package com.ssafy.dodam.domain.loan.service;

import com.ssafy.dodam.domain.loan.entity.Loan;
import com.ssafy.dodam.domain.loan.entity.RepaymentType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 상환일정 계산에 필요한 대출 조건만 뽑은 값.
 *
 * <p>계산기가 엔티티 전체에 의존하지 않도록 분리했다. 조회 경로가 SSAFY 금융망으로 바뀌어도
 * 계산 규칙은 이 값만 채우면 그대로 쓸 수 있다.
 */
public record LoanRepaymentTerms(
        long balance,
        BigDecimal annualRatePercent,
        RepaymentType repaymentType,
        Integer repaymentDay,
        LocalDate nextRepaymentDate,
        LocalDate maturityDate
) {

    public static LoanRepaymentTerms from(Loan loan) {
        return new LoanRepaymentTerms(
                loan.getBalance() == null ? 0L : loan.getBalance(),
                loan.getInterestRate(),
                loan.getRepaymentType(),
                loan.getRepaymentDay(),
                loan.getNextRepaymentDate(),
                loan.getMaturityDate()
        );
    }
}
