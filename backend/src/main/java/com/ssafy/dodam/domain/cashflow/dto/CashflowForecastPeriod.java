package com.ssafy.dodam.domain.cashflow.dto;

import java.time.LocalDate;

/**
 * 예측 구간. 양 끝을 포함하며 일수는 {@code horizonDays}와 같다.
 *
 * <p>종료일을 따로 들고 다니지 않고 매번 파생한다. 두 값을 함께 저장하면 어느 한쪽만 바뀐 상태가
 * 생기고, `cashflow_daily_balance`의 행 수가 `horizon_days`와 어긋난다.
 */
public record CashflowForecastPeriod(LocalDate baseDate, int horizonDays) {

    public LocalDate endDate() {
        return baseDate.plusDays(horizonDays - 1L);
    }
}
