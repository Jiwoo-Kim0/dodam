package com.ssafy.dodam.domain.product.repository;

import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import com.ssafy.dodam.domain.product.entity.ProductType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinancialProductRepository extends
        JpaRepository<FinancialProduct, Long>,
        JpaSpecificationExecutor<FinancialProduct> {

    @Override
    @EntityGraph(attributePaths = "company")
    Page<FinancialProduct> findAll(Specification<FinancialProduct> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"company", "options"})
    Optional<FinancialProduct> findByIdAndProductType(Long id, ProductType productType);

    @EntityGraph(attributePaths = {"company", "options"})
    @Query("select distinct product from FinancialProduct product where product.id in :productIds")
    List<FinancialProduct> findDetailsByIdIn(@Param("productIds") Collection<Long> productIds);

    @EntityGraph(attributePaths = "company")
    List<FinancialProduct> findAllByProductTypeOrderByIdAsc(ProductType productType);
}
