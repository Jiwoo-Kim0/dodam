package com.ssafy.dodam.domain.card.dto.response;

import java.time.LocalDate;
import java.util.List;

public record LowUsageCardsResponse(
        int lookbackMonths,
        long thresholdAmount,
        LocalDate startDate,
        LocalDate endDate,
        List<Card> cards
) {

    public LowUsageCardsResponse {
        cards = List.copyOf(cards);
    }

    public record Card(
            String cardNoMasked,
            String cardName,
            String issuerName,
            Long annualFee,
            long totalUsageAmount,
            long transactionCount
    ) {
    }
}
