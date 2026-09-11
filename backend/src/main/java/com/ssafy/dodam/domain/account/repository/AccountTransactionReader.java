package com.ssafy.dodam.domain.account.repository;

import com.ssafy.dodam.global.policy.QueryPeriod;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 계좌 거래내역 조회 포트. 카드(8)·마이너스통장(10-1)의 거래내역 reader와 같은 방식으로 `transaction`을 JDBC로 읽는다.
 */
public interface AccountTransactionReader {

    /**
     * 숨긴 거래는 제외한다. 정렬은 거래일·거래시각·거래 ID 내림차순.
     */
    Page<AccountTransactionRow> read(Long userId, Long accountId, QueryPeriod period, Pageable pageable);

    /**
     * 단건 조회. 숨긴 거래도 돌려준다(숨김은 목록 노출 정책이지 접근 제한이 아니다).
     */
    Optional<AccountTransactionRow> findOne(Long userId, Long accountId, String transactionId);
}
