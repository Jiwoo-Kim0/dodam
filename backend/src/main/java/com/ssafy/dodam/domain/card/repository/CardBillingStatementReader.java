package com.ssafy.dodam.domain.card.repository;

import com.ssafy.dodam.global.policy.YearMonthPeriod;
import java.util.List;
import java.util.Optional;

/**
 * 카드 결제예정액과 청구서 조회 경계.
 *
 * <p>명세상 데이터 출처는 SSAFY 금융망이지만 현재는 사전 생성된 시연 데이터를
 * 로컬 `card_billing_statement`에서 읽는다. 외부 연동이 붙으면 구현을 교체한다.
 */
public interface CardBillingStatementReader {

    Optional<CardBillingStatementRow> findNextScheduled(Long cardId);

    List<CardBillingStatementRow> read(Long cardId, YearMonthPeriod period);
}
