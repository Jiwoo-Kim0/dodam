package com.ssafy.dodam.domain.savings.dto.response;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * 월 저축액.
 *
 * @param paidThrough 납입 실적을 집계한 마지막 날. 과거 월은 월말, 현재 월은 기준일이다.
 * @param expectedMonthlyAmount 납입 완료 + 저장된 예정 + 약정 추정. 하나라도 확정하지 못하면 null이다.
 * @param calculationBasis 과거 월은 {@code STORED_PAID_ONLY}, 그 외는
 *     {@code PAID_PLUS_REMAINING_MONTHLY_SCHEDULE}
 */
public record SavingsMonthlyAmountResponse(
        YearMonth yearMonth, LocalDate asOfDate, LocalDate paidThrough, Long paidAmount,
        Long scheduledAmount, Long estimatedAmount, Long expectedMonthlyAmount,
        boolean complete, List<String> reasons, String calculationBasis
) {
}
