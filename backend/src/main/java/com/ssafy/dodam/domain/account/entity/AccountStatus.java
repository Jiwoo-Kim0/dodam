package com.ssafy.dodam.domain.account.entity;

/**
 * ERD `account.status` CHECK 제약과 동일한 값. 선언 순서가 목록 정렬 우선순위다(사용 중인 계좌가 먼저).
 */
public enum AccountStatus {
    ACTIVE,
    INACTIVE,
    CLOSED
}
