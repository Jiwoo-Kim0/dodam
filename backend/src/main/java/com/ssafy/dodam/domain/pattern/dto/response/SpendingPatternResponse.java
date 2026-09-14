package com.ssafy.dodam.domain.pattern.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * GET /api/v1/patterns/me/spending?months. 요청 시점에 거래로 다시 계산한다(자체 계산). 금액은 원, 비율은 0~1 소수 4자리.
 *
 * @param startMonth              기간 시작 달(yyyy-MM)
 * @param endMonth                기간 마지막 달(기준일의 전 달)
 * @param monthCount              기간 개월 수
 * @param monthsWithData          거래가 있는 달 수
 * @param totalSpending           기간 소비 합
 * @param monthlyAverage          거래가 있는 달 기준 월평균 소비
 * @param spendingVolatility      월 소비 변동계수. 달이 2개 미만이면 null
 * @param spendingRegularity      1 − 변동계수(0~1)
 * @param fixedExpenseRatio       고정지출 / 소비
 * @param variableExpenseRatio    변동지출 / 소비
 * @param discretionarySpendingRatio 재량소비 / 소비
 * @param spendingToIncomeRatio   월평균 소비 / 월 소득. 소득을 모르면 null
 * @param incomeBasis             TRANSACTIONS | PROFILE | NONE
 * @param monthlyIncome           월 소득. NONE이면 null
 * @param latestMonthAmount       마지막 달 소비
 * @param previousAverage         마지막 달을 뺀 나머지 달(거래 있는 달) 월평균. 없으면 null
 * @param changeRatePercent       (latestMonthAmount − previousAverage) / previousAverage × 100. 계산 불가면 null
 * @param months                  월별 소비(오래된 순, 거래 없는 달 0)
 * @param categories              기간 합 기준 카테고리 내림차순. 미분류는 UNCATEGORIZED
 * @param clusterProfile          최근 완료 분석의 군집 프로필. 없거나 조건 미충족이면 null
 */
public record SpendingPatternResponse(
        String startMonth,
        String endMonth,
        int monthCount,
        int monthsWithData,
        long totalSpending,
        long monthlyAverage,
        Double spendingVolatility,
        Double spendingRegularity,
        Double fixedExpenseRatio,
        Double variableExpenseRatio,
        Double discretionarySpendingRatio,
        Double spendingToIncomeRatio,
        String incomeBasis,
        Long monthlyIncome,
        long latestMonthAmount,
        Long previousAverage,
        Double changeRatePercent,
        List<Month> months,
        List<Category> categories,
        @JsonInclude(JsonInclude.Include.ALWAYS) ClusterProfileResponse clusterProfile
) {

    public SpendingPatternResponse {
        months = List.copyOf(months);
        categories = List.copyOf(categories);
    }

    public record Month(
            String yearMonth,
            long spending,
            long fixedSpending,
            long variableSpending,
            long discretionary,
            long transactionCount
    ) {
    }

    /** @param sharePercent 기간 소비 대비 비중(%). 소비가 0이면 0 */
    public record Category(String code, String name, long amount, long transactionCount, double sharePercent) {
    }
}
