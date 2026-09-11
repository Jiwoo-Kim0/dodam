package com.ssafy.dodam.domain.transaction.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * `transaction`과 `transaction_category`를 조인해 읽은 거래 한 건. 응답 DTO 변환 전의 원본 값이다.
 */
public record TransactionRow(
        String transactionId,
        Long accountId,
        Long cardId,
        Long categoryId,
        String categoryCode,
        String categoryName,
        LocalDate transactionDate,
        LocalTime transactionTime,
        String transactionType,
        long amount,
        Long balanceAfter,
        String merchantName,
        String paymentMethod,
        String spendingType,
        String summary,
        String memo,
        boolean hidden,
        String sourceType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
