package com.ssafy.dodam.domain.savings.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 적금 상세.
 *
 * @param detailsAvailable {@code deposit_saving} 상세행 존재 여부. false면 금리·약정액·기간이 모두 null이다.
 * @param balance 납입 원금 합. 이자를 포함하지 않는다.
 */
public record SavingsDetailResponse(
        Long accountId, String accountName, String accountNoMasked, String bankCode,
        String status, long balance, boolean detailsAvailable, BigDecimal interestRate,
        Long monthlyAmount, Integer paymentDay, LocalDate nextPaymentDate,
        LocalDate openingDate, LocalDate expiryDate, Long financialProductId, LocalDate asOfDate
) {
}
