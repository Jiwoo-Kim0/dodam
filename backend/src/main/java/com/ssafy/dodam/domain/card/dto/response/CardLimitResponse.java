package com.ssafy.dodam.domain.card.dto.response;

import com.ssafy.dodam.domain.card.entity.Card;

public record CardLimitResponse(
        String cardNoMasked,
        long creditLimit
) {

    public static CardLimitResponse from(Card card) {
        return new CardLimitResponse(card.getCardNoMasked(), card.getCreditLimit());
    }
}
