package com.ssafy.dodam.domain.product.repository;

import com.ssafy.dodam.domain.product.entity.FinancialCompany;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialCompanyRepository extends JpaRepository<FinancialCompany, Long> {

    Page<FinancialCompany> findByCompanyNameContainingIgnoreCase(String companyName, Pageable pageable);
}
