package com.ssafy.dodam.domain.loan.dto.response;

import com.ssafy.dodam.domain.loan.entity.Loan;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 보유 대출 목록 항목. 계좌번호는 마스킹 값만 노출한다.
 */
public record LoanResponse(
        Long loanId,
        String accountNoMasked,
        String accountName,
        String institutionCode,
        String institutionName,
        String loanType,
        String repaymentType,
        String status,
        Long originalPrincipal,
        Long balance,
        BigDecimal interestRate,
        Integer repaymentDay,
        LocalDate nextRepaymentDate,
        LocalDate maturityDate
) {

    public static LoanResponse from(Loan loan) {
        return new LoanResponse(
                loan.getId(),
                loan.getAccountNoMasked(),
                loan.getAccountName(),
                loan.getInstitutionCode(),
                loan.getInstitutionName(),
                loan.getLoanType().name(),
                loan.getRepaymentType().name(),
                loan.getStatus().name(),
                loan.getOriginalPrincipal(),
                loan.getBalance(),
                loan.getInterestRate(),
                loan.getRepaymentDay(),
                loan.getNextRepaymentDate(),
                loan.getMaturityDate()
        );
    }
}
