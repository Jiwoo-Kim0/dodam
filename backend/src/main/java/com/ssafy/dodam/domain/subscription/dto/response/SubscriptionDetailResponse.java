package com.ssafy.dodam.domain.subscription.dto.response;

import com.ssafy.dodam.domain.subscription.entity.SubscriptionPaymentHistory;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * 정기결제 상세(GET /subscriptions/{subscriptionId}). 명세의 "SSAFY + 집계": 기본 정보에 결제 이력 집계를 붙인다.
 *
 * @param payments 결제 이력 집계. 이력이 없으면 0·null로 채운 객체(필드는 항상 있다)
 */
public record SubscriptionDetailResponse(
        SubscriptionResponse subscription,
        PaymentStats payments
) {

    /**
     * @param totalPaidAmount   성공한 결제 금액 합
     * @param averagePaidAmount 성공 결제 1건 평균(원 단위 반올림). 성공이 없으면 null
     * @param lastResultCode    가장 최근 시도의 결과 코드(null이면 성공으로 기록된 것)
     */
    public record PaymentStats(
            int paymentCount,
            int successCount,
            int failedCount,
            long totalPaidAmount,
            Long averagePaidAmount,
            LocalDate firstPaymentDate,
            LocalDate lastPaymentDate,
            String lastResultCode
    ) {

        public static PaymentStats from(List<SubscriptionPaymentHistory> histories) {
            int success = 0;
            long paid = 0;
            LocalDate first = null;
            LocalDate last = null;
            SubscriptionPaymentHistory latest = null;
            Comparator<SubscriptionPaymentHistory> latestOrder = Comparator
                    .comparing(SubscriptionPaymentHistory::getPaymentDate)
                    .thenComparing(SubscriptionPaymentHistory::getId);
            for (SubscriptionPaymentHistory history : histories) {
                if (history.isSuccess()) {
                    success++;
                    paid += history.getPaymentAmount();
                }
                LocalDate date = history.getPaymentDate();
                first = first == null || date.isBefore(first) ? date : first;
                last = last == null || date.isAfter(last) ? date : last;
                latest = latest == null || latestOrder.compare(history, latest) > 0 ? history : latest;
            }
            return new PaymentStats(
                    histories.size(),
                    success,
                    histories.size() - success,
                    paid,
                    success == 0 ? null : Math.round((double) paid / success),
                    first,
                    last,
                    latest == null ? null : latest.getResultCode()
            );
        }
    }
}
