package com.ssafy.dodam.domain.card.repository;

import java.time.LocalDate;

public record CardPaymentScheduleRow(
        Long cardId,
        String cardNoMasked,
        String cardName,
        String billingMonth,
        LocalDate billingDate,
        long scheduledAmount,
        String status
) {
}
