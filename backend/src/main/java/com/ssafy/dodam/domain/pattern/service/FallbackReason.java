package com.ssafy.dodam.domain.pattern.service;

/**
 * HYBRID 요청이 RULE_ONLY로 전환된 사유(518 Fallback). 응답과 `feature_summary_json.fallbackReason`에 그대로 남긴다.
 * 규칙 지표는 어느 경우에도 계산되며, 군집 컬럼만 비운다.
 */
public enum FallbackReason {
    /** 요청이 RULE_ONLY였다. 전환이 아니라 요청대로 한 것이지만 응답 형식을 맞추기 위해 사유로 둔다 */
    RULE_ONLY_REQUESTED,
    /** 학습 가능한 사용자 수가 `app.pattern.min-population`보다 적다 */
    INSUFFICIENT_POPULATION,
    /** 모델 아티팩트가 없고(`app.pattern.model-location`) 인프로세스 학습도 꺼져 있다(`train-in-process=false`) */
    MODEL_NOT_AVAILABLE,
    /** 사용자 데이터가 `app.pattern.min-user-months`(기획안: 3개월)보다 짧다 */
    USER_DATA_TOO_SHORT,
    /** 소득을 알 수 없거나 소비가 없어 비율 특징을 만들 수 없다 */
    FEATURES_UNAVAILABLE,
    /** 배정된 군집의 표본 수가 `app.pattern.min-cluster-sample-size`보다 적다 */
    SMALL_CLUSTER_SAMPLE,
    /** 분류 신뢰도(가장 가까운 중심과 두 번째 중심의 거리 차 비율)가 하한보다 낮다 */
    LOW_CONFIDENCE,
    /** 모델 학습·배정 중 예외. 사유는 서버 로그에만 남긴다 */
    MODEL_FAILURE
}
