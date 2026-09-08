package com.ssafy.dodam.domain.card.dto.response;

import com.ssafy.dodam.domain.card.entity.Card;
import com.ssafy.dodam.domain.card.repository.CardBillingStatementRow;

public record CardPaymentScheduleResponse(
        String cardNoMasked,
        CardPaymentScheduleItemResponse schedule
) {

    public static CardPaymentScheduleResponse from(
            Card card,
            CardBillingStatementRow statement
    ) {
        return new CardPaymentScheduleResponse(
                card.getCardNoMasked(),
                statement == null ? null : CardPaymentScheduleItemResponse.from(statement)
        );
    }
}
