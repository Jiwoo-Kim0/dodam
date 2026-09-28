package com.ssafy.dodam.domain.card.dto.response;

import com.ssafy.dodam.domain.card.repository.CardTransactionRow;
import java.time.LocalDate;
import java.time.LocalTime;

public record CardTransactionResponse(
        String transactionId,
        LocalDate transactionDate,
        LocalTime transactionTime,
        String transactionType,
        long amount,
        String merchantName,
        String paymentMethod,
        String categoryCode,
        String categoryName,
        String summary,
        String memo
) {

    public static CardTransactionResponse from(CardTransactionRow row) {
        return new CardTransactionResponse(
                row.transactionId(),
                row.transactionDate(),
                row.transactionTime(),
                row.transactionType(),
                row.amount(),
                row.merchantName(),
                row.paymentMethod(),
                row.categoryCode(),
                row.categoryName(),
                row.summary(),
                row.memo()
        );
    }
}
