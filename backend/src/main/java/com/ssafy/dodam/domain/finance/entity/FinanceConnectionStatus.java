package com.ssafy.dodam.domain.finance.entity;

/**
 * ERD `finance_connection.status` CHECK 제약과 동일한 값.
 */
public enum FinanceConnectionStatus {
    PENDING,
    CONNECTED,
    DISCONNECTED,
    FAILED
}
