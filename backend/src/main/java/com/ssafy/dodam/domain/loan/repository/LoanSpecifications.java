package com.ssafy.dodam.domain.loan.repository;

import com.ssafy.dodam.domain.loan.entity.Loan;
import com.ssafy.dodam.domain.loan.entity.LoanStatus;
import com.ssafy.dodam.domain.loan.entity.LoanType;
import org.springframework.data.jpa.domain.Specification;

public final class LoanSpecifications {

    private LoanSpecifications() {
    }

    public static Specification<Loan> ownedBy(Long userId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("userId"), userId);
    }

    public static Specification<Loan> hasStatus(LoanStatus status) {
        if (status == null) {
            return unrestricted();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), status);
    }

    public static Specification<Loan> hasLoanType(LoanType loanType) {
        if (loanType == null) {
            return unrestricted();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(
                root.get("loanType"),
                loanType
        );
    }

    private static Specification<Loan> unrestricted() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
    }
}
