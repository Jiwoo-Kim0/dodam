package com.ssafy.dodam.domain.subscription.service;

import com.ssafy.dodam.domain.subscription.entity.BillingCycle;
import com.ssafy.dodam.domain.subscription.entity.Subscription;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 재개·결제일 변경 시 다음 결제일을 다시 잡는 규칙. 오늘 이후(오늘 포함) 가장 가까운 결제 예정일을 돌려준다.
 *
 * <ul>
 *   <li>MONTHLY: `payment_day`(없으면 기존 다음 결제일이나 생성일의 일자)를 이번 달에 맞추고, 지났으면 다음 달. 말일은 그 달 길이로 보정</li>
 *   <li>WEEKLY: 기존 다음 결제일(없으면 생성일)을 기준으로 7일씩 더해 오늘 이후로 맞춘다</li>
 *   <li>YEARLY: 기존 다음 결제일(없으면 생성일)의 월·일을 유지하며 1년씩 더한다</li>
 * </ul>
 */
final class NextPaymentDateCalculator {

    private NextPaymentDateCalculator() {
    }

    static LocalDate nextOnOrAfter(Subscription subscription, LocalDate today) {
        LocalDate anchor = subscription.getNextPaymentDate() != null
                ? subscription.getNextPaymentDate()
                : subscription.getCreatedAt().toLocalDate();
        BillingCycle cycle = subscription.getBillingCycle();

        if (cycle == BillingCycle.MONTHLY) {
            int day = subscription.getPaymentDay() != null ? subscription.getPaymentDay() : anchor.getDayOfMonth();
            LocalDate candidate = today.withDayOfMonth(Math.min(day, today.lengthOfMonth()));
            if (candidate.isBefore(today)) {
                LocalDate nextMonth = today.plusMonths(1);
                candidate = nextMonth.withDayOfMonth(Math.min(day, nextMonth.lengthOfMonth()));
            }
            return candidate;
        }
        if (!anchor.isBefore(today)) {
            return anchor;
        }
        if (cycle == BillingCycle.WEEKLY) {
            long days = ChronoUnit.DAYS.between(anchor, today);
            long weeks = (days + 6) / 7;
            return anchor.plusWeeks(weeks);
        }
        LocalDate candidate = anchor;
        while (candidate.isBefore(today)) {
            candidate = candidate.plusYears(1);
        }
        return candidate;
    }
}
