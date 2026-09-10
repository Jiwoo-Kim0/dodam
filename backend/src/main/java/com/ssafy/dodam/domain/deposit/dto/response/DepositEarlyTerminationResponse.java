package com.ssafy.dodam.domain.deposit.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 예금 중도해지 예상금액.
 *
 * <p>계약이율이 아니라 <b>중도해지이율</b>로 계산한 참고 금액이다. 실제 해지 금액은 은행이 확정한다.
 *
 * @param status COMPLETE 또는 UNAVAILABLE. UNAVAILABLE이면 금액 필드가 모두 null이다.
 * @param elapsedDays 개설일부터 기준일까지의 예치일수. 해지 당일은 이자 계산에서 제외한다.
 * @param appliedRate 적용 중도해지이율(연 %)
 * @param ratePolicy 중도해지이율 산정 정책 코드
 * @param rateTier 적용된 경과기간 구간
 * @param estimatedPayoutAmount 원금 + 세후이자
 */
public record DepositEarlyTerminationResponse(
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
        Long elapsedDays,
        Long elapsedMonths,
        BigDecimal appliedRate,
        String ratePolicy,
        String rateTier,
        Long grossInterest,
        Long tax,
        Long netInterest,
        Long estimatedPayoutAmount
) {
}
