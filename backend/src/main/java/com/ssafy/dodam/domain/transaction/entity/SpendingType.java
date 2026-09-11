package com.ssafy.dodam.domain.transaction.entity;

/**
 * ERD `transaction.spending_type`·`transaction_category.default_spending_type` CHECK 제약과 동일한 값.
 * 고정·변동지출 분류(API 6 `/spending/fixed`, `/spending/variable`)의 기준이 된다.
 */
public enum SpendingType {
    FIXED,
    VARIABLE,
    NON_SPENDING
}
