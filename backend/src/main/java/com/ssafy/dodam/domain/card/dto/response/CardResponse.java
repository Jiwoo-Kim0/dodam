package com.ssafy.dodam.domain.card.dto.response;

import com.ssafy.dodam.domain.card.entity.Card;

public record CardResponse(
        String cardNoMasked,
        String cardName,
        String issuerCode,
        String issuerName,
        String status,
        Long creditLimit,
        Long annualFee,
        String expiryMonth,
        Integer withdrawalDay,
        CardWithdrawalAccountResponse withdrawalAccount
) {

    public static CardResponse from(Card card) {
        return new CardResponse(
                card.getCardNoMasked(),
                card.getCardName(),
                card.getIssuerCode(),
                card.getIssuerName(),
                card.getStatus().name(),
                card.getCreditLimit(),
                card.getAnnualFee(),
                card.getExpiryMonth(),
                card.getWithdrawalDay(),
                CardWithdrawalAccountResponse.from(card.getWithdrawalAccount())
        );
    }
}
