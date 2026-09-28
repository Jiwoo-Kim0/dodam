package com.ssafy.dodam.domain.loan.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 한 비교군·기간 밴드·상환방식 조건에서 계산한 기준금리와 후보.
 *
 * @param referenceRate 상품 대표값들의 중앙값. 표본이 부족하면 null이다.
 * @param confidence 표본이 2개면 LOW. 중앙값의 통계적 의미가 약함을 숨기지 않는다.
 * @param candidates 상품 대표값 오름차순, productId 오름차순으로 정렬된 전체 후보. topN은 응답에서 자른다.
 * @param unmatchedConditions 판정에 사용하지 않은 조건. 기간 밴드 채택과 상환방식 모호함 등.
 */
record LoanRateReference(
        BigDecimal referenceRate,
        String confidence,
        int comparableProductCount,
        List<LoanRateCandidate> candidates,
        List<String> unmatchedConditions,
        LocalDateTime oldestSyncedAt,
        LocalDateTime latestSyncedAt
) {
    static final String CONFIDENCE_LOW = "LOW";
    static final String CONFIDENCE_NORMAL = "NORMAL";
    /** 서로 다른 상품이 2개 미만이면 중앙값을 만들지 않는다. 담보·자동차 비교군은 상품 자체가 3~5개다. */
    static final int MINIMUM_PRODUCTS = 2;

    boolean sufficient() {
        return referenceRate != null;
    }
}
