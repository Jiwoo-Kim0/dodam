package com.ssafy.dodam.domain.finance.event;

/**
 * 금융망 연결이 CONNECTED로 커밋된 뒤 발행한다. 발행 지점은 트랜잭션 밖이어야 한다.
 * 받는 쪽(도담 전용 계좌 개설)이 금융망을 다시 호출하므로 가입·연결 트랜잭션에 묶지 않는다.
 */
public record FinanceConnectedEvent(Long userId) {
}
