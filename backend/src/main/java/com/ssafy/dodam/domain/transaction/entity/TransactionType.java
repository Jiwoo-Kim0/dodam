package com.ssafy.dodam.domain.transaction.entity;

/**
 * ERD `transaction.transaction_type` CHECK 제약과 동일한 값. 금액은 항상 양수이고 방향은 이 값이 정한다.
 */
public enum TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER
}
