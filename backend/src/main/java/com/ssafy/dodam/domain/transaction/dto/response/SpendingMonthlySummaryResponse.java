package com.ssafy.dodam.domain.transaction.dto.response;

/**
 * 월 소비 요약(GET /spending/monthly-summary). 금액 단위는 원.
 *
 * @param daysElapsed             집계에 쓰인 일수. 이번 달이면 오늘까지, 지난 달이면 그 달 전체
 * @param dailyAverage            totalSpending / daysElapsed (반올림)
 * @param averagePerTransaction   totalSpending / transactionCount (반올림), 거래가 없으면 0
 * @param previousMonthSpending   전월 전체 소비
 * @param changeFromPreviousMonth totalSpending − previousMonthSpending
 * @param changeRatePercent       전월 대비 증감률(%). 전월이 0이면 null
 */
public record SpendingMonthlySummaryResponse(
        String yearMonth,
        long totalSpending,
        long transactionCount,
        int daysElapsed,
        int daysInMonth,
        long dailyAverage,
        long averagePerTransaction,
        long previousMonthSpending,
        long changeFromPreviousMonth,
        Double changeRatePercent
) {
}
