package com.ssafy.dodam.domain.transaction.dto.response;

import java.util.List;

/**
 * 결제수단별 소비(GET /spending/payment-methods). 소비 금액 내림차순. 결제수단이 없는 거래는 `paymentMethod = "UNKNOWN"`으로 한 묶음이다.
 *
 * @param sharePercent totalSpending 대비 비중(소수 첫째 자리). 합계가 0이면 0
 */
public record PaymentMethodSpendingResponse(
        String yearMonth,
        long totalSpending,
        long transactionCount,
        List<Item> paymentMethods
) {

    public PaymentMethodSpendingResponse {
        paymentMethods = List.copyOf(paymentMethods);
    }

    public record Item(
            String paymentMethod,
            long totalSpending,
            long transactionCount,
            double sharePercent
    ) {
    }
}
