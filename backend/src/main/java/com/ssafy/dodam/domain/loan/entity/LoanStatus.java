package com.ssafy.dodam.domain.loan.entity;

/**
 * ERD `loan.status` CHECK 제약과 동일한 대출 상태 값.
 *
 * <p>선언 순서가 곧 목록 노출 우선순위다. 상태를 문자열로 저장하므로 DB 정렬은 사전순이 되어
 * 연체가 종료된 대출보다 뒤로 밀린다. 목록은 이 순서로 다시 정렬한다.
 */
public enum LoanStatus {
    /** 상환 중. */
    ACTIVE,
    /** 연체. 사용자가 가장 먼저 확인해야 하므로 상환 중 대출 바로 다음에 둔다. */
    OVERDUE,
    /** 정상 상환 완료. */
    COMPLETED,
    /** 중도 해지 등으로 종료. */
    CLOSED;

    /**
     * 잔액이 남아 상환이 진행 중인 상태. 요약과 상환일정 계산의 기본 대상이다.
     */
    public boolean isOutstanding() {
        return this == ACTIVE || this == OVERDUE;
    }
}
