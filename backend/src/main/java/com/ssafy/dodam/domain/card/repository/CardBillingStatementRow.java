package com.ssafy.dodam.domain.card.repository;

import com.ssafy.dodam.domain.card.entity.CardBillingStatementStatus;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 카드 청구서 한 건의 조회 결과.
 */
public record CardBillingStatementRow(
        YearMonth billingMonth,
        LocalDate billingDate,
        long totalBalance,
        CardBillingStatementStatus status,
        LocalDate paymentDate
) {
}
