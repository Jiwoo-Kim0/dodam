package com.ssafy.dodam.domain.minusaccount.repository;

import com.ssafy.dodam.global.policy.QueryPeriod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 마이너스통장 거래내역 조회.
 *
 * <p>`transaction`은 BE-A 소관이라 JPA 엔티티로 선점하지 않고 읽기 전용 어댑터만 둔다.
 * 카드의 `CardTransactionReader`와 같은 방식이다.
 */
public interface MinusAccountTransactionReader {

    Page<MinusAccountTransactionRow> read(
            Long userId,
            Long accountId,
            QueryPeriod period,
            Pageable pageable
    );
}
