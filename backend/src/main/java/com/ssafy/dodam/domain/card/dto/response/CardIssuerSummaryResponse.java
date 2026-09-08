package com.ssafy.dodam.domain.card.dto.response;

public record CardIssuerSummaryResponse(
        String issuerCode,
        String issuerName,
        long cardCount,
        long activeCardCount,
        long totalCreditLimit
) {
}
