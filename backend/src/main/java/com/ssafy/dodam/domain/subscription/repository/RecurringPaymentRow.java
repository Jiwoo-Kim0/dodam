package com.ssafy.dodam.domain.subscription.repository;

import java.time.LocalDate;

/**
 * 반복결제 탐지(513)가 읽는 거래 한 건. 거래 원장(`transaction`)의 EXPENSE·숨김 아님 행만 온다.
 */
public record RecurringPaymentRow(
        String transactionId,
        Long cardId,
        LocalDate transactionDate,
        long amount,
        String merchantName,
        String categoryCode
) {
}
