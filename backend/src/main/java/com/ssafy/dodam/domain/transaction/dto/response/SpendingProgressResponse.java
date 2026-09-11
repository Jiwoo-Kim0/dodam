package com.ssafy.dodam.domain.transaction.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 월 누적 소비(GET /spending/progress). 월 1일부터 기준일(이번 달이면 오늘, 지난 달이면 말일)까지 하루씩 누적한다.
 *
 * @param projectedMonthEnd 현재 일평균 × 그 달 일수. 지난 달이면 totalSpending과 같다. 거래가 없으면 0
 */
public record SpendingProgressResponse(
        String yearMonth,
        LocalDate asOfDate,
        int daysElapsed,
        int daysInMonth,
        long totalSpending,
        long dailyAverage,
        long projectedMonthEnd,
        List<Day> days
) {

    public SpendingProgressResponse {
        days = List.copyOf(days);
    }

    public record Day(
            LocalDate date,
            long dailySpending,
            long transactionCount,
            long cumulativeSpending
    ) {
    }
}
