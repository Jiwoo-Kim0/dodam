package com.ssafy.dodam.domain.card.dto.response;

import java.math.BigDecimal;

public record CardPaymentBurdenResponse(
        String yearMonth,
        long totalBillingAmount,
        Long monthlyIncome,
        BigDecimal burdenRate
) {
}
