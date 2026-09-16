package com.ssafy.dodam.domain.improvement.entity;

/**
 * 개선 행동 상태. `chk_improvement_action_status`의 값 집합과 같다.
 *
 * <p>API 19에는 상태를 직접 바꾸는 엔드포인트가 없다. 전이는 두 곳에서만 일어난다.
 *
 * <pre>
 * 추천 ACCEPTED            → PLANNED      (수락 시각 = plannedAt)
 * 측정 기간 시작(관측 개시) → IN_PROGRESS  (ImprovementMeasurementService가 조회 시점에 전이)
 * 추천 COMPLETED           → COMPLETED
 * 추천 REJECTED(수락 이후)  → CANCELED
 * </pre>
 *
 * <p>COMPLETED·CANCELED는 종료 상태다. 종료된 뒤에도 측정은 계속할 수 있다(완료한 행동의 효과를 재는 것이
 * 이 API의 목적이므로). 취소된 행동은 측정하지 않는다.
 */
public enum ImprovementActionStatus {
    PLANNED, IN_PROGRESS, COMPLETED, CANCELED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELED;
    }

    /** 취소되지 않았으면 효과 측정 대상이다. */
    public boolean isMeasurable() {
        return this != CANCELED;
    }
}
