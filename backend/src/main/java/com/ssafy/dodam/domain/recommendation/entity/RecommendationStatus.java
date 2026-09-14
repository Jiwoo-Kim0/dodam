package com.ssafy.dodam.domain.recommendation.entity;

/**
 * 추천 상태. `chk_recommendation_status`·`chk_recommendation_history_status`의 값 집합과 같다.
 *
 * <p>상태 전이 허용표는 여기 두지 않는다. accept/reject/complete/defer의 전이·멱등 정책은
 * S15P21D105-549가 정한다. 신규 추천은 항상 {@link #PENDING}이다.
 */
public enum RecommendationStatus {
    PENDING, ACCEPTED, REJECTED, DEFERRED, COMPLETED
}
