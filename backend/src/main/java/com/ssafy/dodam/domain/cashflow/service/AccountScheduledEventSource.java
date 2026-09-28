package com.ssafy.dodam.domain.cashflow.service;

import com.ssafy.dodam.domain.cashflow.dto.CashflowPlannedEvent.Direction;
import java.time.LocalDate;
import java.util.List;

/**
 * 계좌 예정 거래 제공 경계. 구현체는 userId 소유권을 조회 조건으로 검증해야 한다.
 * sourceType/sourceId/occurrence는 원본 도메인 회차 식별자이며 민감번호를 넣지 않는다.
 * 카드·대출·적금 출금을 표현할 때도 해당 도메인의 식별자를 유지하여 중복을 제거한다.
 */
public interface AccountScheduledEventSource {
    List<AccountEvent> read(Long userId, LocalDate start, LocalDate end);

    record AccountEvent(Long userId, LocalDate date, String type, String name, Direction direction,
            long amount, String sourceType, String sourceId, String occurrence) { }
}
