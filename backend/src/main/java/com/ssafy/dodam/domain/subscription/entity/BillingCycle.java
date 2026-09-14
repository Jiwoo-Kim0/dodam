package com.ssafy.dodam.domain.subscription.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * ERD `subscription.billing_cycle` CHECK 제약과 동일한 값. 월 환산 금액과 다음 결제일 계산 규칙을 함께 둔다.
 */
public enum BillingCycle {
    WEEKLY,
    MONTHLY,
    YEARLY;

    /** 요약(월 합계)에서 쓰는 월 환산 금액. WEEKLY는 52주/12개월, YEARLY는 1/12. 원 단위 반올림. */
    public long toMonthlyAmount(long amount) {
        return switch (this) {
            case MONTHLY -> amount;
            case WEEKLY -> BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(52))
                    .divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP).longValue();
            case YEARLY -> BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP).longValue();
        };
    }

    /** 기준일 다음 주기의 날짜. MONTHLY는 결제일이 있으면 그 날짜(말일 보정), 없으면 기준일의 일자를 쓴다. */
    public LocalDate next(LocalDate from, Integer paymentDay) {
        return switch (this) {
            case WEEKLY -> from.plusWeeks(1);
            case YEARLY -> from.plusYears(1);
            case MONTHLY -> {
                LocalDate nextMonth = from.plusMonths(1);
                int day = paymentDay == null ? from.getDayOfMonth() : paymentDay;
                yield nextMonth.withDayOfMonth(Math.min(day, nextMonth.lengthOfMonth()));
            }
        };
    }

    /** 평균 결제 간격(일)으로 주기를 추정한다. 어느 주기에도 들지 않으면 null(불규칙). */
    public static BillingCycle estimate(double averageIntervalDays) {
        if (averageIntervalDays >= 5 && averageIntervalDays <= 9) {
            return WEEKLY;
        }
        if (averageIntervalDays >= 25 && averageIntervalDays <= 36) {
            return MONTHLY;
        }
        if (averageIntervalDays >= 350 && averageIntervalDays <= 380) {
            return YEARLY;
        }
        return null;
    }
}
