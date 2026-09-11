package com.ssafy.dodam.domain.transaction.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 일별 소비(GET /spending/daily). 기간 안의 모든 날을 채우고 거래가 없는 날은 0이다.
 */
public record DailySpendingResponse(
        LocalDate startDate,
        LocalDate endDate,
        long totalSpending,
        long transactionCount,
        long dailyAverage,
        List<Day> days
) {

    public DailySpendingResponse {
        days = List.copyOf(days);
    }

    public record Day(
            LocalDate date,
            long totalSpending,
            long transactionCount
    ) {
    }
}
