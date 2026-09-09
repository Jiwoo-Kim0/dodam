package com.ssafy.dodam.domain.loan.entity;

/**
 * ERD `loan.repayment_type` CHECK 제약과 동일한 상환 방식 값.
 *
 * <p>상환일정 계산식이 이 값에 따라 달라진다.
 */
public enum RepaymentType {
    /** 원금균등: 매 회차 원금이 같고 이자는 잔액에 따라 줄어든다. */
    EQUAL_PRINCIPAL,
    /** 원리금균등: 매 회차 상환액(원금 + 이자)이 같다. */
    EQUAL_PAYMENT,
    /** 만기일시: 매월 이자만 내고 만기에 원금을 한 번에 상환한다. */
    BULLET
}
