package com.ssafy.dodam.domain.loan.repository;

import com.ssafy.dodam.domain.loan.entity.Loan;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LoanRepository extends
        JpaRepository<Loan, Long>,
        JpaSpecificationExecutor<Loan> {

    Optional<Loan> findByUserIdAndAccountNoHash(Long userId, String accountNoHash);

    Optional<Loan> findByIdAndUserId(Long id, Long userId);
}
