package com.ssafy.dodam.domain.loan.dto.response;

import com.ssafy.dodam.domain.loan.entity.Loan;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 대출 상세. 목록 항목에 기간과 상환 진행 정보를 더한다.
 */
public record LoanDetailResponse(
        String accountNoMasked,
        String accountName,
        String institutionCode,
        String institutionName,
        String loanType,
        String repaymentType,
        String status,
        Long originalPrincipal,
        Long balance,
        Long repaidPrincipal,
        BigDecimal interestRate,
        Integer loanPeriod,
        Integer repaymentDay,
        LocalDate startDate,
        LocalDate maturityDate,
        LocalDate nextRepaymentDate
) {

    public static LoanDetailResponse from(Loan loan) {
        long originalPrincipal = loan.getOriginalPrincipal() == null ? 0L : loan.getOriginalPrincipal();
        long balance = loan.getBalance() == null ? 0L : loan.getBalance();
        return new LoanDetailResponse(
                loan.getAccountNoMasked(),
                loan.getAccountName(),
                loan.getInstitutionCode(),
                loan.getInstitutionName(),
                loan.getLoanType().name(),
                loan.getRepaymentType().name(),
                loan.getStatus().name(),
                loan.getOriginalPrincipal(),
                loan.getBalance(),
                // ERD가 상환 원금을 따로 보관하지 않으므로 원금과 잔액의 차이로 계산한다.
                originalPrincipal - balance,
                loan.getInterestRate(),
                loan.getLoanPeriod(),
                loan.getRepaymentDay(),
                loan.getStartDate(),
                loan.getMaturityDate(),
                loan.getNextRepaymentDate()
        );
    }
}
