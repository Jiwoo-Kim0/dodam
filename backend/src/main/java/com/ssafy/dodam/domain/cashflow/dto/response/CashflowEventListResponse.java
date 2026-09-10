package com.ssafy.dodam.domain.cashflow.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 예정 금융이벤트 조회 응답. 구간 두 개의 의미는
 * {@link CashflowDailyBalanceListResponse}와 같다.
 *
 * @param type 적용된 선택 필터. 지정하지 않으면 null이다
 */
public record CashflowEventListResponse(
        CashflowForecastResponse forecast,
        LocalDate startDate,
        LocalDate endDate,
        String type,
        List<CashflowEventResponse> events
) {
}
