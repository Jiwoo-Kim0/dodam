package com.ssafy.dodam.domain.subscription.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 정기결제 요약(GET /subscriptions/summary). 금액 합계는 ACTIVE만, 건수는 상태별로 전부 센다.
 *
 * @param monthlyTotalAmount    ACTIVE 정기결제의 월 환산 금액 합
 * @param yearlyTotalAmount     monthlyTotalAmount × 12
 * @param nextPaymentDate       ACTIVE 중 가장 가까운 다음 결제일. 없으면 null
 * @param upcomingWithin7Days   오늘부터 7일 안(오늘 포함) 결제 예정 건수·금액
 * @param upcomingWithin30Days  오늘부터 30일 안 결제 예정 건수·금액
 * @param byCategory            ACTIVE 정기결제의 카테고리별 건수·월 환산 금액. 월 금액 내림차순
 */
public record SubscriptionSummaryResponse(
        int totalCount,
        int activeCount,
        int pausedCount,
        int expiredCount,
        int canceledCount,
        long monthlyTotalAmount,
        long yearlyTotalAmount,
        LocalDate nextPaymentDate,
        Upcoming upcomingWithin7Days,
        Upcoming upcomingWithin30Days,
        List<CategoryAmount> byCategory
) {

    public record Upcoming(int count, long amount) {
    }

    /**
     * @param category null 카테고리는 "ETC"로 묶는다
     */
    public record CategoryAmount(String category, String categoryLabel, int count, long monthlyAmount) {
    }
}
