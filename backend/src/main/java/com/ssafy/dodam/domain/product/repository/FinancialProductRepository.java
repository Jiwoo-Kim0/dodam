package com.ssafy.dodam.domain.product.repository;

import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import com.ssafy.dodam.domain.product.entity.ProductType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

public interface FinancialProductRepository extends
        JpaRepository<FinancialProduct, Long>,
        JpaSpecificationExecutor<FinancialProduct> {

    @Override
    @EntityGraph(attributePaths = "company")
    Page<FinancialProduct> findAll(Specification<FinancialProduct> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"company", "options"})
    Optional<FinancialProduct> findByIdAndProductType(Long id, ProductType productType);
}
