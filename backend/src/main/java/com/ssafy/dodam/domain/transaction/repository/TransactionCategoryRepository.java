package com.ssafy.dodam.domain.transaction.repository;

import com.ssafy.dodam.domain.transaction.entity.TransactionCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionCategoryRepository extends JpaRepository<TransactionCategory, Long> {

    Optional<TransactionCategory> findByCode(String code);

    Optional<TransactionCategory> findByIdAndActiveTrue(Long id);

    List<TransactionCategory> findAllByActiveTrueOrderByIdAsc();
}
