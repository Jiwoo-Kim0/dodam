package com.ssafy.dodam.domain.deposit.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 본인 예금 계좌 한 건의 조회 결과.
 *
 * <p>계좌번호 원문·해시·암호문은 담지 않는다. 소유권 판별은 조회 조건에서 끝나고 이후 계층은 마스킹 값만
 * 다룬다.
 *
 * @param detailsAvailable {@code deposit_saving} 상세행 존재 여부. false면 금리·개설일·만기일이 모두 null이다.
 *     계좌 자체는 존재하므로 404로 감추지 않는다.
 * @param balance 원금 잔액. 이자를 포함하지 않는다(524 seed의 balance 규약).
 */
public record DepositAccountRow(
        long accountId,
        String accountName,
        String bankCode,
        String bankName,
        String accountNoMasked,
        String status,
        long balance,
        BigDecimal interestRate,
        Long financialProductId,
        LocalDate openingDate,
        LocalDate expiryDate,
        boolean detailsAvailable
) {
}
