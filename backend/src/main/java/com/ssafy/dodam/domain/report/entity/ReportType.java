package com.ssafy.dodam.domain.report.entity;

/** `report.type` CHECK 제약과 같은 값. 상위행의 유형이 하위행 테이블을 고른다(복합 FK로 강제된다). */
public enum ReportType {
    DAILY, MONTHLY
}
