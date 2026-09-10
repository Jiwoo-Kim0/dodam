package com.ssafy.dodam.domain.minusaccount.dto.response;

import com.ssafy.dodam.domain.minusaccount.entity.MinusAccount;
import com.ssafy.dodam.domain.minusaccount.entity.MinusAccountOwner;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 마이너스통장 상세. 목록 항목에 계좌 일반 정보를 더한다.
 *
 * @param balance 상위 `account`의 잔액. MINUS 계좌는 `-usedAmount`인 0 이하 값이다(ERD 계산 기준).
 */
public record MinusAccountDetailResponse(
        Long accountId,
        String bankCode,
        String bankName,
        String accountName,
        String accountNoMasked,
        String status,
        Long limitAmount,
        Long usedAmount,
        Long availableAmount,
        BigDecimal usageRate,
        BigDecimal interestRate,
        Long accruedInterest,
        LocalDate nextInterestDate,
        LocalDate maturityDate,
        Integer overdueDays,
        boolean overdue,
        Long balance,
        String currency,
        LocalDate lastTransactionDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MinusAccountDetailResponse of(MinusAccountOwner owner, MinusAccount detail) {
        return new MinusAccountDetailResponse(
                detail.getAccountId(),
                owner.getBankCode(),
                owner.getBankName(),
                owner.getAccountName(),
                owner.getAccountNoMasked(),
                owner.getStatus(),
                detail.getLimitAmount(),
                detail.getUsedAmount(),
                detail.getAvailableAmount(),
                MinusAccountResponse.usageRate(detail),
                detail.getInterestRate(),
                detail.getAccruedInterest(),
                detail.getNextInterestDate(),
                detail.getMaturityDate(),
                detail.getOverdueDays(),
                detail.isOverdue(),
                owner.getBalance(),
                owner.getCurrency(),
                owner.getLastTransactionDate(),
                detail.getCreatedAt(),
                detail.getUpdatedAt()
        );
    }
}
