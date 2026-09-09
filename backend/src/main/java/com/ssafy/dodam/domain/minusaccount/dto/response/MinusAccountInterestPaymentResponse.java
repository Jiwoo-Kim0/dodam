package com.ssafy.dodam.domain.minusaccount.dto.response;

import java.time.LocalDateTime;

/**
 * 이자 납부 결과. 원금(`usedAmount`)은 바뀌지 않으므로 확인용으로 함께 돌려준다.
 *
 * <p>상환과 마찬가지로 거래내역에 기록을 남기지 않는다.
 */
public record MinusAccountInterestPaymentResponse(
        Long accountId,
        String accountNoMasked,
        Long paidInterest,
        Long accruedInterest,
        Long usedAmount,
        String paymentAccountNoMasked,
        Long paymentAccountBalance,
        LocalDateTime processedAt
) {
}
