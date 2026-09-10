package com.ssafy.dodam.domain.diagnosis.entity;

/**
 * 문제를 탐지한 근거(`diagnosis_issue.detection_source`).
 *
 * <p>명세 14는 규칙 기반 진단에 군집(ML) 결과를 보조로 병합하되 <b>군집 결과가 없으면 규칙 기반으로
 * 진단한다</b>고 정한다. 따라서 기본값은 {@link #RULE}이고, 군집 모델이 붙기 전에 저장되는 모든
 * 행은 `RULE`이다.
 *
 * <p>`CLUSTER`와 `RULE_AND_CLUSTER`를 실제로 채우는 것은 군집 신호 병합을 구현하는
 * S15P21D105-545의 몫이다.
 */
public enum DetectionSource {
    RULE,
    CLUSTER,
    RULE_AND_CLUSTER
}
