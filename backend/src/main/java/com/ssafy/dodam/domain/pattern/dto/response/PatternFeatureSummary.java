package com.ssafy.dodam.domain.pattern.dto.response;

import java.time.LocalDateTime;
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
 * @param sourceFingerprint 분석 기간 원천 거래의 지문(700). 같은 날 재사용 판정에 쓴다. 700 이전 분석은 null
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
        List<Month> months,
        SourceFingerprint sourceFingerprint
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

    /**
     * 분석 기간 안 사용자 거래의 지문(S15P21D105-700). 숨긴 거래도 포함해 집계한다.
     *
     * <p>수동 거래 삭제는 행을 지우므로 `updated_at`만으로는 감지할 수 없다. 건수·금액 합으로 삭제를, `created_at` 최댓값으로
     * 추가를, `updated_at` 최댓값으로 숨김·카테고리·금액 변경을 잡는다. 기간 밖 거래는 집계에 들어가지 않으므로 지문이
     * 바뀌지 않는다.
     *
     * @param transactionCount 기간 안 거래 건수(숨김 포함)
     * @param amountSum        기간 안 거래 금액 합(숨김 포함)
     * @param lastCreatedAt    기간 안 거래의 마지막 생성 시각. 거래가 없으면 null
     * @param lastUpdatedAt    기간 안 거래의 마지막 수정 시각. 수정된 거래가 없으면 null
     */
    public record SourceFingerprint(
            long transactionCount,
            long amountSum,
            LocalDateTime lastCreatedAt,
            LocalDateTime lastUpdatedAt
    ) {
    }
}
