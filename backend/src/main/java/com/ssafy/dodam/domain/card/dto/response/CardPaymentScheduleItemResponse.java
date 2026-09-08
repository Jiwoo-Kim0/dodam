package com.ssafy.dodam.domain.card.dto.response;

import com.ssafy.dodam.domain.card.repository.CardBillingStatementRow;
import java.time.LocalDate;

public record CardPaymentScheduleItemResponse(
        String billingMonth,
        LocalDate billingDate,
        long scheduledAmount,
        String status
) {

    public static CardPaymentScheduleItemResponse from(CardBillingStatementRow row) {
        return new CardPaymentScheduleItemResponse(
                row.billingMonth().toString(),
                row.billingDate(),
                row.totalBalance(),
                row.status().name()
        );
    }
}
