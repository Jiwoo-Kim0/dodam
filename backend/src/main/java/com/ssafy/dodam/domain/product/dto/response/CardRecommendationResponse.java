package com.ssafy.dodam.domain.product.dto.response;

import java.util.List;

public record CardRecommendationResponse(
        String yearMonth,
        long totalSpending,
        boolean dataAvailable,
        List<RecommendedCardResponse> recommendations
) {

    public CardRecommendationResponse {
        recommendations = List.copyOf(recommendations);
    }
}
