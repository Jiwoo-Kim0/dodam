package com.ssafy.dodam.domain.savings.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 본인 적금 계좌 한 건의 조회 결과.
 *
 * <p>계좌번호 원문·해시·암호문은 담지 않는다. 소유권 판별은 조회 조건에서 끝나고 이후 계층은 마스킹 값만
 * 다룬다.
 *
 * @param detailsAvailable {@code deposit_saving} 상세행 존재 여부. false면 금리·약정액·기간이 모두 null이다.
 * @param balance 납입 원금 합. 이자를 포함하지 않는다(524 seed의 balance 규약).
 */
public record SavingsAccountRow(
        Long accountId, String accountName, String accountNoMasked, String bankCode, String bankName,
        String status, long balance, boolean detailsAvailable, BigDecimal interestRate,
        Long monthlyAmount, Integer paymentDay, LocalDate nextPaymentDate,
        LocalDate openingDate, LocalDate expiryDate, Long financialProductId
) {
}
