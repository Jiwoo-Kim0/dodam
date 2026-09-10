package com.ssafy.dodam.domain.finance.entity;

/**
 * ERD `finance_sync.status` CHECK 제약과 동일한 값.
 * 동기화는 요청 스레드에서 바로 실행하므로 현재 구현에서 PENDING은 쓰지 않는다 (비동기 전환 시 예약).
 */
public enum FinanceSyncStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED;

    public boolean isInProgress() {
        return this == PENDING || this == RUNNING;
    }
}
