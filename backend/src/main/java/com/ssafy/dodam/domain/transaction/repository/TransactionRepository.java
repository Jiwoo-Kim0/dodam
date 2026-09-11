package com.ssafy.dodam.domain.transaction.repository;

import com.ssafy.dodam.domain.transaction.entity.Transaction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 거래 원장의 쓰기 경로. 목록·상세 조회는 카테고리 조인이 필요해 {@link TransactionReader}가 맡는다.
 */
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    /**
     * 본인 소유 거래만 찾는다. 타인 거래는 존재 여부를 노출하지 않도록 없는 것과 같게 취급한다.
     */
    Optional<Transaction> findByIdAndUserId(String id, Long userId);
}
