package com.ssafy.dodam.domain.card.dto.response;

import com.ssafy.dodam.domain.card.repository.CardBillingStatementRow;
import java.time.LocalDate;

public record CardBillingStatementResponse(
        String billingMonth,
        LocalDate billingDate,
        long totalBalance,
        String status,
        LocalDate paymentDate
) {

    public static CardBillingStatementResponse from(CardBillingStatementRow row) {
        return new CardBillingStatementResponse(
                row.billingMonth().toString(),
                row.billingDate(),
                row.totalBalance(),
                row.status().name(),
                row.paymentDate()
        );
    }
}
