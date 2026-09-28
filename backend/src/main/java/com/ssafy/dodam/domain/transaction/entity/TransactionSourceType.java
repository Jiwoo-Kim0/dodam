package com.ssafy.dodam.domain.transaction.entity;

/**
 * ERD `transaction.source_type` CHECK 제약과 동일한 값.
 * 수동 등록 거래(MANUAL)만 사용자가 수정·삭제할 수 있고, 나머지는 메모·카테고리·숨김만 바꿀 수 있다.
 */
public enum TransactionSourceType {
    SSAFY,
    MANUAL,
    SEED
}
