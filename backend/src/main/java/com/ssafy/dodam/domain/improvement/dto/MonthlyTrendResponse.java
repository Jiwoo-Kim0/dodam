package com.ssafy.dodam.domain.improvement.dto;

import java.util.List;

/**
 * 월별 개선추이(API 19 #10).
 *
 * <p>{@code months}는 빈 달을 0으로 채워 <b>연속</b>으로 내려준다. 차트가 구멍을 메우지 않아도 되도록.
 *
 * <p>월 배정 기준이 항목마다 다르다. 섞으면 같은 행동이 두 달에 걸쳐 중복 집계된다.
 * <ul>
 *   <li>{@code completedActions} — 그 달에 <b>완료된</b>(completedAt) 행동 수</li>
 *   <li>금액 4종 — 그 달에 <b>측정된</b>(measuredAt) 행동의 실측값 합. 측정은 행동당 1회다</li>
 * </ul>
 */
public record MonthlyTrendResponse(String startMonth, String endMonth, List<Month> months) {

    public MonthlyTrendResponse {
        months = months == null ? List.of() : List.copyOf(months);
    }

    public record Month(
            String yearMonth,
            long completedActions,
            long monthlySaving,
            long spendingReduction,
            long savingIncrease,
            long debtReduction
    ) {
    }
}
