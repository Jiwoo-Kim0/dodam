package com.ssafy.dodam.domain.card.repository;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 카드 사용내역 한 건의 조회 결과.
 */
public record CardTransactionRow(
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
}
