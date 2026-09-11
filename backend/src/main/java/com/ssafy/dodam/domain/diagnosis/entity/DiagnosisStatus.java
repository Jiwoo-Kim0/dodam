package com.ssafy.dodam.domain.diagnosis.entity;

/**
 * 진단 헤더의 종합 상태(`diagnosis.overall_status`).
 *
 * <p>ERD `chk_diagnosis_overall_status`의 값 집합이다. 계산이 끝나기 전에는 NULL이므로 이 enum에
 * `PENDING` 같은 진행 상태를 넣지 않는다. 실행 상태는 `completed_at`으로 판별한다
 * ({@link Diagnosis} 참고).
 *
 * <p>판정 규칙(어떤 문제 조합이 `CAUTION`인지 `RISK`인지)은 진단 실행을 구현하는
 * S15P21D105-544에서 정한다.
 */
public enum DiagnosisStatus {
    HEALTHY,
    CAUTION,
    RISK
}
