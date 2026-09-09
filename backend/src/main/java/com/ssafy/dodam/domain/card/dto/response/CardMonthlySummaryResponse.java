package com.ssafy.dodam.domain.card.dto.response;

public record CardMonthlySummaryResponse(
        String cardNoMasked,
        String yearMonth,
        long totalAmount,
        long transactionCount
) {
}
