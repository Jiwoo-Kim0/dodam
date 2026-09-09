package com.ssafy.dodam.domain.card.dto.response;

import com.ssafy.dodam.domain.product.dto.response.CardBenefitResponse;
import java.util.List;

public record CardBenefitsResponse(
        String cardNoMasked,
        boolean benefitDataAvailable,
        List<CardBenefitResponse> benefits
) {

    public CardBenefitsResponse {
        benefits = List.copyOf(benefits);
    }
}
