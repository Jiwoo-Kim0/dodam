package com.ssafy.dodam.domain.transaction.dto.response;

import java.util.List;

/**
 * 소비 급증 조회(GET /spending/anomalies). 기획안 「변동지출 급증」 규칙:
 * 최근 N개월 카테고리 평균지출 > 이전 N개월 카테고리 평균지출 × (1 + 기준 증가율).
 *
 * @param recentPeriod          비교 대상(요청 기간). 기본 최근 3개월
 * @param baselinePeriod        직전 같은 길이의 기간
 * @param thresholdRatePercent  기준 증가율(%)
 * @param minimumIncrease       소액 보정: 월평균 증가액이 이 금액보다 작으면 급증으로 보지 않는다
 * @param category              카테고리 필터를 줬으면 그 코드
 * @param evaluatedCategories   두 기간 중 한 곳이라도 소비가 있어 판정 대상이 된 카테고리 수
 * @param anomalies             증가율 내림차순
 */
public record SpendingAnomalyResponse(
        Period recentPeriod,
        Period baselinePeriod,
        double thresholdRatePercent,
        long minimumIncrease,
        String category,
        int evaluatedCategories,
        List<Item> anomalies
) {

    public SpendingAnomalyResponse {
        anomalies = List.copyOf(anomalies);
    }

    public record Period(String startMonth, String endMonth, int months) {
    }

    /**
     * @param recentAverage       최근 기간 월평균 소비
     * @param baselineAverage     이전 기간 월평균 소비
     * @param increaseAmount      recentAverage − baselineAverage
     * @param increaseRatePercent increaseAmount / baselineAverage (%)
     */
    public record Item(
            Long categoryId,
            String code,
            String name,
            long recentAverage,
            long baselineAverage,
            long increaseAmount,
            double increaseRatePercent
    ) {
    }
}
