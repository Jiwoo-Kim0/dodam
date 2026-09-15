package com.ssafy.dodam.domain.simulation.dto;

import com.ssafy.dodam.domain.loan.entity.LoanStatus;
import com.ssafy.dodam.domain.loan.entity.LoanType;
import com.ssafy.dodam.domain.loan.entity.RepaymentType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 실행 시점에 읽은 본인 대출 조건. 계좌번호 원문·해시·마스킹값은 싣지 않는다 — 결과 JSON에 그대로 저장되기 때문이다.
 * 대출 자료는 로컬 스냅샷(`loan` 테이블)이며 요청 중 금융망을 호출하지 않는다.
 *
 * @param productLoanType 연결 상품의 loan_type 문자열. 대환 비교군 판정에 쓴다. 연결 상품이 없으면 null
 */
public record LoanSnapshot(
        Long loanId,
        String accountName,
        String institutionName,
        LoanType loanType,
        RepaymentType repaymentType,
        LoanStatus status,
        long balance,
        BigDecimal interestRate,
        Integer repaymentDay,
        LocalDate nextRepaymentDate,
        LocalDate maturityDate,
        Long financialProductId,
        String productLoanType
) {
}
