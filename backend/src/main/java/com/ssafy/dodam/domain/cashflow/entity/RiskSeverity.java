package com.ssafy.dodam.domain.cashflow.entity;

/**
 * 현금흐름 위험 심각도. `diagnosis_issue.severity`와 같은 코드 집합을 쓴다(ERD 주요 코드값 표).
 */
public enum RiskSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
