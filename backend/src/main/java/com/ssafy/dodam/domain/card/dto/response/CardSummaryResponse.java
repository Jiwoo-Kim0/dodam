package com.ssafy.dodam.domain.card.dto.response;

import java.util.List;

public record CardSummaryResponse(
        long totalCardCount,
        long activeCardCount,
        long totalCreditLimit,
        long totalAnnualFee,
        List<CardIssuerSummaryResponse> issuers
) {

    public CardSummaryResponse {
        issuers = List.copyOf(issuers);
    }
}
