package com.ssafy.dodam.domain.forecast.service;

/**
 * 예측이 축약된 경로로 나왔을 때의 사유(S15P21D105-706).
 *
 * <p>어휘는 {@code pattern}의 {@code FallbackReason}을 따르되 enum은 따로 둔다. 도메인 간 코드값 enum을 재사용하지 않는 것은
 * {@code IssueSeverity}가 {@code cashflow}의 {@code RiskSeverity}를 쓰지 않은 것과 같은 판단이다. 어휘까지 새로 만들면 같은
 * 상황이 도메인마다 다른 이름으로 불린다.
 */
public enum ForecastFallbackReason {
    /** 계절성을 뺀 값이 아니다. 축약 없이 추정했다 */
    NONE,
    /** 이력이 12개월보다 짧아 달력 월별 계수를 만들 수 없다. 계절 계수를 모두 1로 두고 수준·추세만 쓴다 */
    SEASONALITY_UNAVAILABLE,
    /** 이력이 {@code MIN_MONTHS}보다 짧다. 예측하지 않는다 */
    USER_DATA_TOO_SHORT,
    /** 추정에 쓸 관측이 2개 미만이다. 예측하지 않는다 */
    FEATURES_UNAVAILABLE
}
