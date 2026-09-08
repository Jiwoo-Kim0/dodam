package com.ssafy.dodam.domain.card.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CardCategorySummaryResponse(
        String cardNoMasked,
        String yearMonth,
        long totalAmount,
        List<Category> categories
) {

    public CardCategorySummaryResponse {
        categories = List.copyOf(categories);
    }

    public record Category(
            String categoryCode,
            String categoryName,
            long amount,
            long transactionCount,
            BigDecimal ratio
    ) {
    }
}
