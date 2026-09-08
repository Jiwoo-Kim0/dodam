package com.ssafy.dodam.domain.card.repository;

import com.ssafy.dodam.global.policy.QueryPeriod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 카드 사용내역 조회 경계.
 *
 * <p>명세상 데이터 출처는 SSAFY 금융망이지만 현재는 사전 생성된 시연 데이터를
 * 로컬 `transaction`에서 읽는다. 외부 연동이 붙으면 이 인터페이스의 구현만 교체한다.
 */
public interface CardTransactionReader {

    Page<CardTransactionRow> read(Long userId, Long cardId, QueryPeriod period, Pageable pageable);
}
