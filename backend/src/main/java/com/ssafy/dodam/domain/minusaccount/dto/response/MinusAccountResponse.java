package com.ssafy.dodam.domain.minusaccount.dto.response;

import com.ssafy.dodam.domain.minusaccount.entity.MinusAccount;
import com.ssafy.dodam.domain.minusaccount.entity.MinusAccountOwner;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 마이너스통장 목록 항목. 계좌번호는 마스킹 값만 노출한다.
 *
 * @param usageRate     한도 소진 비율. 백분율이 아니라 0~1 사이의 비율이다(0.9700 = 97%).
 *                      {@code interestRate}가 8.1000처럼 퍼센트 값이라 단위가 다르다.
 * @param interestRate  연이율(%)
 * @param overdue       연체 여부. 별도 상태 컬럼이 없어 {@code overdueDays > 0}으로 판정한다.
 */
public record MinusAccountResponse(
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
        boolean overdue
) {

    private static final int RATE_SCALE = 4;

    public static MinusAccountResponse of(MinusAccountOwner owner, MinusAccount detail) {
        return new MinusAccountResponse(
                detail.getAccountId(),
                owner.getBankCode(),
                owner.getBankName(),
                owner.getAccountName(),
                owner.getAccountNoMasked(),
                owner.getStatus(),
                detail.getLimitAmount(),
                detail.getUsedAmount(),
                detail.getAvailableAmount(),
                usageRate(detail),
                detail.getInterestRate(),
                detail.getAccruedInterest(),
                detail.getNextInterestDate(),
                detail.getMaturityDate(),
                detail.getOverdueDays(),
                detail.isOverdue()
        );
    }

    /**
     * `chk_minus_account_limit_amount`가 한도를 양수로 보장하므로 0 나눗셈은 발생하지 않는다.
     */
    static BigDecimal usageRate(MinusAccount detail) {
        return BigDecimal.valueOf(detail.getUsedAmount())
                .divide(BigDecimal.valueOf(detail.getLimitAmount()), RATE_SCALE, RoundingMode.HALF_UP);
    }
}
