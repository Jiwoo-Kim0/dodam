package com.ssafy.dodam.domain.deposit.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 예금 상세.
 *
 * @param dataSource 자료 출처. 현재는 로컬 시연 DB만 사용한다.
 * @param detailsAvailable {@code deposit_saving} 상세행 존재 여부. false면 금리·기간 필드가 모두 null이다.
 * @param balance 원금 잔액(원). 이자를 포함하지 않는다.
 * @param contractMonths 개설일부터 만기일까지의 만 개월 수. 만기일이 없으면 null이다.
 * @param daysUntilMaturity 기준일부터 만기일까지 남은 일수. 이미 지났으면 음수다.
 */
public record DepositDetailResponse(
        LocalDate asOfDate,
        String dataSource,
        Long accountId,
        String accountName,
        String bankCode,
        String bankName,
        String accountNoMasked,
        String accountType,
        String status,
        Long balance,
        boolean detailsAvailable,
        BigDecimal interestRate,
        LocalDate openingDate,
        LocalDate expiryDate,
        Integer contractMonths,
        Long contractDays,
        Long daysUntilMaturity,
        Boolean matured,
        Long productId,
        String productName,
        String institutionName,
        String savingType,
        LocalDateTime productSyncedAt
) {
}
