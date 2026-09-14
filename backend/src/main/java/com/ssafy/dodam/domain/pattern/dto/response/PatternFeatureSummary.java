package com.ssafy.dodam.domain.pattern.dto.response;

import java.util.List;
import java.util.Map;

/**
 * `pattern_analysis.feature_summary_json`의 형식. 분석 시점의 입력 요약을 그대로 남겨 조회 때 재계산하지 않는다.
 * 응답 `featureSummary`에도 이 형식으로 나간다.
 *
 * @param requestedMode   요청 모드(RULE_ONLY | HYBRID)
 * @param analysisMode    실제 적용 모드. HYBRID 요청이 전환되면 RULE_ONLY
 * @param fallbackReason  군집 결과가 없는 사유. 군집이 붙었으면 null
 * @param windowStart     분석 기간 시작 달(yyyy-MM)
 * @param windowEnd       분석 기간 마지막 달
 * @param monthCount      기간 개월 수
 * @param monthsWithData  거래가 있는 달 수
 * @param incomeBasis     TRANSACTIONS | PROFILE | NONE
 * @param monthlyIncome   월 소득(원). NONE이면 null
 * @param averages        거래가 있는 달 기준 월평균(원)
 * @param spendingVolatility 월 소비 변동계수
 * @param liquidBalance   입출금 활성 계좌 잔액 합. 계좌 정보 없으면 null
 * @param runwayMonths    잔액 / 월평균 소비
 * @param populationSize  군집 모델 학습 표본 수. 모델까지 가지 못했으면 null
 * @param clusterFeatures 군집 입력 특징(키 → 값). 만들 수 없었으면 빈 객체
 * @param months          월별 집계(오래된 순, 거래 없는 달 0)
 */
public record PatternFeatureSummary(
        String requestedMode,
        String analysisMode,
        String fallbackReason,
        String windowStart,
        String windowEnd,
        int monthCount,
        int monthsWithData,
        String incomeBasis,
        Long monthlyIncome,
        Averages averages,
        Double spendingVolatility,
        Long liquidBalance,
        Double runwayMonths,
        Integer populationSize,
        Map<String, Double> clusterFeatures,
        List<Month> months
) {

    public PatternFeatureSummary {
        clusterFeatures = clusterFeatures == null ? Map.of() : Map.copyOf(clusterFeatures);
        months = months == null ? List.of() : List.copyOf(months);
    }

    public record Averages(
            long income,
            long spending,
            long fixedSpending,
            long variableSpending,
            long discretionary,
            long savingTransfer,
            long loanRepayment
    ) {
    }

    public record Month(
            String yearMonth,
            long income,
            long spending,
            long fixedSpending,
            long variableSpending,
            long discretionary,
            long savingTransfer,
            long loanRepayment,
            long transactionCount
    ) {
    }
}
