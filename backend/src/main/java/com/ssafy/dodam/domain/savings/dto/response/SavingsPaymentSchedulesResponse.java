package com.ssafy.dodam.domain.savings.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 기간 내 납입 예정.
 *
 * @param complete 예정을 확정하지 못한 계좌가 하나도 없으면 true
 * @param totalAmount 예정 금액 합. 범위를 넘으면 null이다.
 */
public record SavingsPaymentSchedulesResponse(
        LocalDate asOfDate, LocalDate periodStart, LocalDate periodEnd, boolean complete,
        List<String> reasons, Long totalAmount, List<SavingsScheduleItemResponse> schedules
) {
}
