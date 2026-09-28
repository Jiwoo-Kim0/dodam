package com.ssafy.dodam.domain.card.dto.response;

import com.ssafy.dodam.domain.card.entity.Card;
import java.time.LocalDateTime;

public record CardDetailResponse(
        Long cardId,
        String cardNoMasked,
        String cardName,
        String issuerCode,
        String issuerName,
        String status,
        Long creditLimit,
        Long annualFee,
        Long baselinePerformance,
        Long maxBenefitLimit,
        String expiryMonth,
        Integer withdrawalDay,
        Long financialProductId,
        CardWithdrawalAccountResponse withdrawalAccount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CardDetailResponse from(Card card) {
        return new CardDetailResponse(
                card.getId(),
                card.getCardNoMasked(),
                card.getCardName(),
                card.getIssuerCode(),
                card.getIssuerName(),
                card.getStatus().name(),
                card.getCreditLimit(),
                card.getAnnualFee(),
                card.getBaselinePerformance(),
                card.getMaxBenefitLimit(),
                card.getExpiryMonth(),
                card.getWithdrawalDay(),
                card.getFinancialProductId(),
                CardWithdrawalAccountResponse.from(card.getWithdrawalAccount()),
                card.getCreatedAt(),
                card.getUpdatedAt()
        );
    }
}
