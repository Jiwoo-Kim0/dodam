package com.ssafy.dodam.domain.pattern.entity;

/**
 * ERD `pattern_analysis.status` CHECK 제약과 동일한 값.
 *
 * <p>접수 직후 RUNNING, 계산이 끝나면 COMPLETED다. 예상하지 못한 계산 오류는 FAILED로 남겨 원인을 추적한다.
 * 데이터 부족처럼 사용자에게 바로 알려야 하는 선행 조건 위반은 행을 남기지 않고 접수를 폐기한다.
 * PENDING은 ERD 호환을 위해 두며 현재 동기 실행에서는 쓰지 않는다.
 */
public enum PatternAnalysisStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
