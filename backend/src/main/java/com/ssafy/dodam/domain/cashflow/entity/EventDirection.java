package com.ssafy.dodam.domain.cashflow.entity;

/**
 * 예정 금융이벤트의 입·출금 방향.
 *
 * <p>금액은 방향과 무관하게 항상 양수로 저장하고 방향은 이 값으로만 구분한다(ERD 주요 코드값 표).
 */
public enum EventDirection {
    INFLOW,
    OUTFLOW
}
