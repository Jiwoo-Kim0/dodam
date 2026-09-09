package com.ssafy.dodam.domain.card.repository;

public record LowUsageCardRow(
        Long cardId,
        String cardNoMasked,
        String cardName,
        String issuerName,
        Long annualFee,
        long totalUsageAmount,
        long transactionCount
) {
}
