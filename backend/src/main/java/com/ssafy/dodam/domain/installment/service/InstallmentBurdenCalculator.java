package com.ssafy.dodam.domain.installment.service;

import com.ssafy.dodam.domain.installment.dto.response.InstallmentBurdenResponse;
import com.ssafy.dodam.domain.installment.dto.response.InstallmentBurdenResponse.AvailableAmountBasis;
import com.ssafy.dodam.domain.installment.dto.response.InstallmentMonthResponse;
import com.ssafy.dodam.domain.installment.dto.response.InstallmentScheduleResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Component;

/**
 * 한 달치 할부 일정과 가용금액 근거를 부담률 응답으로 합친다.
 *
 * <p>비율은 일정이 완전하고 가용금액이 양수일 때만 낸다. 일정 불완전 → 가용금액 미산출 → 분모 0 이하 순으로
 * 먼저 걸리는 사유 하나만 남기고 비율은 null이다. 정상 일정에서 예정액이 0원이면 비율은 0.00이다.
 */
@Component
public class InstallmentBurdenCalculator {

    private static final int RATE_SCALE = 2;

    public InstallmentBurdenResponse calculate(
            InstallmentScheduleResponse schedule, AvailableAmountBasis basis, LocalDate asOfDate
    ) {
        InstallmentMonthResponse month = schedule.months().get(0);
        YearMonth yearMonth = YearMonth.parse(month.yearMonth());
        // 현재 월은 기준일 이후 남은 회차만 예정액에 들어가므로 기간 시작도 기준일로 맞춘다.
        LocalDate periodStart = yearMonth.equals(YearMonth.from(asOfDate)) ? asOfDate : yearMonth.atDay(1);

        Long availableAmount = availableAmount(basis);
        String reason = reason(schedule.complete(), availableAmount);
        BigDecimal rate = reason == null ? rate(month.scheduledAmount(), availableAmount) : null;

        return new InstallmentBurdenResponse(
                asOfDate, month.yearMonth(), periodStart, yearMonth.atEndOfMonth(),
                month.scheduledAmount(), month.installmentCount(),
                schedule.complete(), schedule.excludedInstallmentCount(), schedule.exclusions(),
                availableAmount, basis, rate, reason);
    }

    private Long availableAmount(AvailableAmountBasis basis) {
        if (basis.monthlyIncome() == null) {
            return null;
        }
        long amount = Math.subtractExact(basis.monthlyIncome(), basis.fixedExpense());
        amount = Math.subtractExact(amount, basis.monthlySaving());
        return Math.subtractExact(amount, basis.monthlyLoanRepayment());
    }

    private String reason(boolean complete, Long availableAmount) {
        if (!complete) {
            return InstallmentBurdenResponse.INCOMPLETE_SCHEDULE;
        }
        if (availableAmount == null) {
            return InstallmentBurdenResponse.AVAILABLE_AMOUNT_UNAVAILABLE;
        }
        if (availableAmount <= 0) {
            return InstallmentBurdenResponse.NON_POSITIVE_AVAILABLE_AMOUNT;
        }
        return null;
    }

    private BigDecimal rate(long scheduledAmount, long availableAmount) {
        return BigDecimal.valueOf(scheduledAmount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(availableAmount), RATE_SCALE, RoundingMode.HALF_UP);
    }
}
