package com.ssafy.dodam.domain.transaction.repository;

import com.ssafy.dodam.global.policy.QueryPeriod;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 거래 목록·상세 조회 포트. 카테고리 코드·이름을 함께 읽기 위해 JDBC 조인으로 구현한다.
 */
public interface TransactionReader {

    /**
     * @param filter   조회 조건. 기간은 필수이고 나머지는 null이면 조건에서 제외한다.
     * @param pageable page·size. 정렬은 거래일·거래시각·거래 ID 내림차순으로 고정한다.
     */
    Page<TransactionRow> read(Long userId, TransactionFilter filter, Pageable pageable);

    Optional<TransactionRow> findOne(Long userId, String transactionId);

    /**
     * @param period        조회 기간(양끝 포함)
     * @param categoryCode  카테고리 코드. null이면 전체
     * @param paymentMethod 결제수단. null이면 전체
     * @param hidden        숨김 여부. null이면 전체, false면 숨기지 않은 거래만, true면 숨긴 거래만
     */
    record TransactionFilter(
            QueryPeriod period,
            String categoryCode,
            String paymentMethod,
            Boolean hidden
    ) {
    }
}
