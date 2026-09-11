package com.ssafy.dodam.domain.account.repository;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * `transaction`과 `transaction_category`를 조인해 읽은 계좌 거래 한 건.
 * 마이너스통장(10-1) 거래내역 응답과 같은 열 구성이다(응답 계약이 갈라지지 않게 맞춘 것).
 */
public record AccountTransactionRow(
        String transactionId,
        LocalDate transactionDate,
        LocalTime transactionTime,
        String transactionType,
        long amount,
        Long balanceAfter,
        String merchantName,
        String summary,
        String memo,
        String categoryCode,
        String categoryName,
        boolean hidden
) {
}
