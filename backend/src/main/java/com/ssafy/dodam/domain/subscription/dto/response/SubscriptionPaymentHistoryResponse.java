package com.ssafy.dodam.domain.subscription.dto.response;

import com.ssafy.dodam.domain.subscription.entity.SubscriptionPaymentHistory;
import java.time.LocalDate;

/**
 * 결제 이력 한 건(GET /subscriptions/{subscriptionId}/history). 최신순.
 *
 * @param success `resultCode`가 null이거나 SUCCESS
 */
public record SubscriptionPaymentHistoryResponse(
        Long paymentHistoryId,
        LocalDate paymentDate,
        long paymentAmount,
        String resultCode,
        boolean success,
        int retryCount,
        String resultMessage
) {

    public static SubscriptionPaymentHistoryResponse from(SubscriptionPaymentHistory history) {
        return new SubscriptionPaymentHistoryResponse(
                history.getId(),
                history.getPaymentDate(),
                history.getPaymentAmount(),
                history.getResultCode(),
                history.isSuccess(),
                history.getRetryCount(),
                history.getResultMessage()
        );
    }
}
