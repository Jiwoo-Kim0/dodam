package com.ssafy.dodam.domain.deposit.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 예금 만기 예상금액.
 *
 * <p>계약이율을 만기까지 유지했을 때의 참고 금액이다. 우대금리와 만기 후 이율은 반영하지 않는다.
 *
 * @param status COMPLETE 또는 UNAVAILABLE. UNAVAILABLE이면 금액 필드가 모두 null이다.
 * @param contractDays 개설일부터 만기일까지의 계약일수
 * @param daysUntilMaturity 기준일부터 만기일까지 남은 일수. 이미 지났으면 음수다.
 * @param matured 기준일에 이미 만기가 지났는지 여부. true여도 계약 기준 금액은 계산한다.
 * @param estimatedPayoutAmount 원금 + 세후이자
 */
public record DepositMaturityForecastResponse(
        LocalDate asOfDate,
        List<String> assumptions,
        Long accountId,
        String accountName,
        String accountNoMasked,
        String bankName,
        String status,
        List<String> unavailableReasons,
        Long principal,
        BigDecimal contractRate,
        LocalDate openingDate,
        LocalDate expiryDate,
        Long contractDays,
        Long daysUntilMaturity,
        Boolean matured,
        Long grossInterest,
        Long tax,
        Long netInterest,
        Long estimatedPayoutAmount
) {
}
