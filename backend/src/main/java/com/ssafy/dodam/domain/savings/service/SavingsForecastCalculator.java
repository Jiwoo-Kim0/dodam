package com.ssafy.dodam.domain.savings.service;

import com.ssafy.dodam.domain.savings.dto.response.SavingsForecastResponse;
import com.ssafy.dodam.domain.savings.repository.SavingsAccountRow;
import com.ssafy.dodam.domain.savings.repository.SavingsPaymentRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

@Component
public class SavingsForecastCalculator {
    private final SavingsScheduleCalculator schedules;

    public SavingsForecastCalculator(SavingsScheduleCalculator schedules) {
        this.schedules = schedules;
    }

    public SavingsForecastResponse calculate(
            SavingsAccountRow account, List<SavingsPaymentRow> allPayments, LocalDate today, boolean early
    ) {
        var reasons = new TreeSet<String>();
        List<String> assumptions = new ArrayList<>(List.of(
                "MONTHLY_CONTRIBUTIONS", "ACTUAL_DAYS_365_SIMPLE_INTEREST",
                "EXCLUDES_TAX_AND_FEES", "NO_INTEREST_AFTER_MATURITY"));
        assumptions.add(early ? "ESTIMATED_EARLY_TERMINATION_RATE" : "FIXED_CURRENT_RATE");
        // 중도해지는 계약이율이 아니라 경과기간 구간별 중도해지이율을 적용한다. 이율 0%는 "이자가 없다"는
        // 계산된 답처럼 보이지만 실제 적금 중도해지에는 낮더라도 이율이 붙는다.
        long elapsedMonths = account.openingDate() == null ? 0
                : Math.max(0, ChronoUnit.MONTHS.between(account.openingDate(), today));
        BigDecimal rate = early
                ? SavingsEarlyTerminationRatePolicy.resolve(
                        account.interestRate() == null ? BigDecimal.ZERO : account.interestRate(), elapsedMonths)
                : account.interestRate();
        List<SavingsPaymentRow> rows = allPayments.stream()
                .filter(row -> row.accountId().equals(account.accountId())).toList();
        List<SavingsPaymentRow> paid = rows.stream().filter(row -> "PAID".equals(row.status()))
                .filter(row -> !row.paymentDate().isAfter(today)).toList();
        Long principal = SavingsAmounts.sum(paid, SavingsPaymentRow::amount);
        if ("CLOSED".equals(account.status())) {
            reasons.add("ACCOUNT_CLOSED");
        }
        if (!account.detailsAvailable() || account.openingDate() == null || account.expiryDate() == null
                || account.interestRate() == null) {
            reasons.add("INCOMPLETE_SAVINGS_TERMS");
        }
        if (principal == null) {
            reasons.add("AMOUNT_OUT_OF_RANGE");
        } else if (principal != account.balance()) {
            reasons.add("PAYMENT_HISTORY_BALANCE_MISMATCH");
        }
        if (rows.stream().anyMatch(row -> "PAID".equals(row.status()) && row.paymentDate().isAfter(today))) {
            reasons.add("FUTURE_PAID_PAYMENT");
        }
        LocalDate expiry = account.expiryDate();
        if (account.openingDate() != null && (account.openingDate().isAfter(today)
                || (expiry != null && expiry.isBefore(account.openingDate())))) {
            reasons.add("INVALID_CONTRACT_DATES");
        }
        if (account.openingDate() != null && paid.stream().anyMatch(row ->
                row.paymentDate().isBefore(account.openingDate())
                        || (expiry != null && row.paymentDate().isAfter(expiry)))) {
            reasons.add("INVALID_PAYMENT_DATES");
        }
        LocalDate calculationDate = expiry == null ? null : early && today.isBefore(expiry) ? today : expiry;
        Long futurePrincipal = 0L;
        BigDecimal interest = BigDecimal.ZERO;
        if (reasons.isEmpty()) {
            // 회차마다 실제 예치일수를 따로 적용한다. 모든 원금이 가입일에 예치된 것으로 계산하지 않는다.
            for (var payment : paid) {
                interest = interest.add(weightedPrincipal(payment.amount(), payment.paymentDate(), calculationDate));
            }
            if (!early && today.isBefore(expiry)) {
                if (ChronoUnit.MONTHS.between(today, expiry) > 600) {
                    reasons.add("FORECAST_PERIOD_TOO_LONG");
                } else if (!"ACTIVE".equals(account.status())) {
                    reasons.add("ACCOUNT_NOT_ACTIVE");
                } else {
                    var schedule = schedules.calculate(List.of(account), rows, today, expiry, today);
                    reasons.addAll(schedule.reasons());
                    if (rows.stream().anyMatch(row -> "SCHEDULED".equals(row.status())
                            && row.paymentDate().isBefore(today))) {
                        reasons.add("OVERDUE_SCHEDULED_PAYMENT");
                    }
                    futurePrincipal = schedule.totalAmount();
                    for (var payment : schedule.schedules()) {
                        if (!payment.paymentDate().isBefore(expiry)) {
                            reasons.add("INVALID_PAYMENT_DATES");
                        } else {
                            interest = interest.add(weightedPrincipal(
                                    payment.amount(), payment.paymentDate(), calculationDate));
                        }
                    }
                }
                assumptions.add("FUTURE_MONTHLY_PAYMENTS_AS_SCHEDULED");
            }
        }
        Long expectedPrincipal = null;
        Long expectedInterest = null;
        Long expectedAmount = null;
        if (reasons.isEmpty() && futurePrincipal != null) {
            expectedPrincipal = SavingsAmounts.amount(BigDecimal.valueOf(principal).add(BigDecimal.valueOf(futurePrincipal)));
            expectedInterest = SavingsAmounts.amount(interest.multiply(rate)
                    .divide(BigDecimal.valueOf(36500), 0, RoundingMode.DOWN));
            if (expectedPrincipal == null || expectedInterest == null) {
                reasons.add("AMOUNT_OUT_OF_RANGE");
            } else {
                expectedAmount = SavingsAmounts.amount(
                        BigDecimal.valueOf(expectedPrincipal).add(BigDecimal.valueOf(expectedInterest)));
                if (expectedAmount == null) {
                    reasons.add("AMOUNT_OUT_OF_RANGE");
                }
            }
        }
        return new SavingsForecastResponse(account.accountId(), account.accountNoMasked(), today, calculationDate,
                reasons.isEmpty() ? "COMPLETE" : "UNAVAILABLE", List.copyOf(reasons),
                assumptions.stream().sorted().toList(), rate,
                early ? SavingsEarlyTerminationRatePolicy.POLICY_CODE : null,
                early ? SavingsEarlyTerminationRatePolicy.describe(elapsedMonths) : null,
                early ? elapsedMonths : null, principal, futurePrincipal,
                expectedPrincipal, expectedInterest, expectedAmount);
    }

    private BigDecimal weightedPrincipal(long amount, LocalDate from, LocalDate through) {
        return BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(Math.max(0, ChronoUnit.DAYS.between(from, through))));
    }
}

