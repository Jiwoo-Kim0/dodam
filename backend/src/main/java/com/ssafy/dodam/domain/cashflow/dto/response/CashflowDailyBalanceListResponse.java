package com.ssafy.dodam.domain.cashflow.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 날짜별 예상잔액 조회 응답.
 *
 * <p>`startDate`·`endDate`는 정책이 확정한 **요청 구간**이고, 실제 값이 존재하는 범위는
 * `forecast`의 기준일~종료일이다. 요청 구간이 예측 구간을 벗어난 날짜는 계산된 적이 없으므로
 * 행이 없다. 두 구간을 함께 실어야 호출자가 목록이 비거나 잘린 이유를 구분할 수 있다.
 */
public record CashflowDailyBalanceListResponse(
        CashflowForecastResponse forecast,
        LocalDate startDate,
        LocalDate endDate,
        List<CashflowDailyBalanceResponse> balances
) {
}
