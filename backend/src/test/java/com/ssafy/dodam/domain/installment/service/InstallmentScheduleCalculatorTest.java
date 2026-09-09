package com.ssafy.dodam.domain.installment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.domain.installment.dto.response.InstallmentMonthResponse;
import com.ssafy.dodam.domain.installment.entity.InstallmentExclusionReason;
import com.ssafy.dodam.domain.installment.entity.InstallmentStatus;
import com.ssafy.dodam.domain.installment.repository.InstallmentRow;
import com.ssafy.dodam.global.policy.YearMonthPeriod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstallmentScheduleCalculatorTest {
    private final InstallmentScheduleCalculator calculator = new InstallmentScheduleCalculator();
    private final LocalDate today = LocalDate.of(2026, 9, 8);
    private final YearMonthPeriod period = new YearMonthPeriod(YearMonth.of(2026, 9), YearMonth.of(2026, 12));

    @Test
    void aggregatesRemainingPaymentsAndFillsEmptyMonthsWithoutRecalculatingFees() {
        var result = calculator.calculate(List.of(
                row(1, InstallmentStatus.ACTIVE, today, 2, 120000),
                row(2, InstallmentStatus.ACTIVE, LocalDate.of(2026, 10, 31), 2, 90000),
                row(3, InstallmentStatus.COMPLETED, null, 0, 999999),
                row(4, InstallmentStatus.CANCELED, today, 4, 999999)
        ), period, today);
        assertThat(result.complete()).isTrue();
        assertThat(result.exclusions()).isEmpty();
        assertThat(result.months()).containsExactly(
                new InstallmentMonthResponse("2026-09", 120000, 1),
                new InstallmentMonthResponse("2026-10", 210000, 2),
                new InstallmentMonthResponse("2026-11", 90000, 1),
                new InstallmentMonthResponse("2026-12", 0, 0));
        assertThat(result.asOfDate()).isEqualTo(today);
    }

    @Test
    void distinguishesIncompleteZeroFromEmptyZeroAndUsesOneReasonPerRow() {
        var result = calculator.calculate(List.of(
                row(1, InstallmentStatus.OVERDUE, null, 0, 100),
                row(2, InstallmentStatus.ACTIVE, null, 0, 100),
                row(3, InstallmentStatus.ACTIVE, null, 1, 100),
                row(4, InstallmentStatus.ACTIVE, LocalDate.of(2025, 12, 1), 1, 100),
                row(5, InstallmentStatus.ACTIVE, today.minusDays(1), 1, 100),
                row(6, InstallmentStatus.COMPLETED, null, 0, 100)
        ), period, today);
        assertThat(result.complete()).isFalse();
        assertThat(result.excludedInstallmentCount()).isEqualTo(5);
        assertThat(result.exclusions()).extracting(value -> value.reason())
                .containsExactly(InstallmentExclusionReason.values());
        assertThat(result.exclusions()).allSatisfy(value -> assertThat(value.count()).isEqualTo(1));
        assertThat(result.months()).allSatisfy(value -> assertThat(value.scheduledAmount()).isZero());

        var empty = calculator.calculate(List.of(), period, today);
        assertThat(empty.complete()).isTrue();
        assertThat(empty.excludedInstallmentCount()).isZero();
        assertThat(empty.months()).hasSize(4).allSatisfy(value -> assertThat(value.scheduledAmount()).isZero());
    }

    @Test
    void rejectsPurchaseAfterFirstPaymentAndFutureNextBeforeFirstPayment() {
        var badPurchase = new InstallmentRow(1L, 1L, "테스트", 100, 1, null, 1, 100,
                null, today.plusDays(1), today, today, InstallmentStatus.ACTIVE);
        var badNext = new InstallmentRow(2L, 1L, "테스트", 100, 1, null, 1, 100,
                null, today, today.plusDays(2), today.plusDays(1), InstallmentStatus.ACTIVE);
        var result = calculator.calculate(List.of(badPurchase, badNext), period, today);
        assertThat(result.exclusions()).singleElement().satisfies(value -> {
            assertThat(value.reason()).isEqualTo(InstallmentExclusionReason.INCONSISTENT_PAYMENT_DATES);
            assertThat(value.count()).isEqualTo(2);
        });
    }

    @Test
    void preservesAnchorAfterFebruaryAndHandlesLeapYearAndYearBoundary() {
        LocalDate next = LocalDate.of(2026, 1, 31);
        assertThat(calculator.paymentDate(next, YearMonth.of(2026, 2))).isEqualTo("2026-02-28");
        assertThat(calculator.paymentDate(next, YearMonth.of(2026, 3))).isEqualTo("2026-03-31");
        assertThat(calculator.paymentDate(next, YearMonth.of(2028, 2))).isEqualTo("2028-02-29");
        assertThat(calculator.paymentDate(LocalDate.of(2026, 12, 31), YearMonth.of(2027, 1)))
                .isEqualTo("2027-01-31");
        // 저장된 다음 결제일 자체가 2월 말일이면 원래 31일이었다고 추측하지 않는다.
        assertThat(calculator.paymentDate(LocalDate.of(2026, 2, 28), YearMonth.of(2026, 3)))
                .isEqualTo("2026-03-28");
    }

    @Test
    void truncatesToRequestedRangeAndRemainingCountEvenForHugeTerms() {
        var later = new YearMonthPeriod(YearMonth.of(2026, 11), YearMonth.of(2026, 12));
        var result = calculator.calculate(List.of(
                row(1, InstallmentStatus.ACTIVE, today, 2, 100),
                row(2, InstallmentStatus.ACTIVE, today, Integer.MAX_VALUE, 200),
                row(3, InstallmentStatus.ACTIVE, LocalDate.of(2027, 1, 1), 1, 300)
        ), later, today);
        assertThat(result.months()).containsExactly(
                new InstallmentMonthResponse("2026-11", 200, 1),
                new InstallmentMonthResponse("2026-12", 200, 1));
    }

    @Test
    void detectsAmountOverflowInsteadOfReturningNegativeAmount() {
        assertThatThrownBy(() -> calculator.calculate(List.of(
                row(1, InstallmentStatus.ACTIVE, today, 1, Long.MAX_VALUE),
                row(2, InstallmentStatus.ACTIVE, today, 1, 1)
        ), period, today)).isInstanceOf(ArithmeticException.class);
    }

    private InstallmentRow row(long id, InstallmentStatus status, LocalDate next, int remaining, long amount) {
        return new InstallmentRow(id, 1L, "테스트 상점", amount, Math.max(remaining, 1),
                1L, remaining, amount, new BigDecimal("5.9"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), next, status);
    }
}
