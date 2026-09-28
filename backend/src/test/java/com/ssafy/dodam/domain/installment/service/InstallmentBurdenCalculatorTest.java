package com.ssafy.dodam.domain.installment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.dodam.domain.installment.dto.response.InstallmentBurdenResponse;
import com.ssafy.dodam.domain.installment.dto.response.InstallmentBurdenResponse.AvailableAmountBasis;
import com.ssafy.dodam.domain.installment.dto.response.InstallmentExclusionResponse;
import com.ssafy.dodam.domain.installment.dto.response.InstallmentMonthResponse;
import com.ssafy.dodam.domain.installment.dto.response.InstallmentScheduleResponse;
import com.ssafy.dodam.domain.installment.entity.InstallmentExclusionReason;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstallmentBurdenCalculatorTest {

    private final InstallmentBurdenCalculator calculator = new InstallmentBurdenCalculator();
    private final LocalDate today = LocalDate.of(2026, 9, 16);

    @Test
    void dividesScheduledAmountByIncomeLessFixedSavingAndLoan() {
        var result = calculator.calculate(schedule("2026-09", 120000, 1, true),
                basis(1_000_000L, 300_000, 100_000, 50_000), today);

        assertThat(result.availableAmount()).isEqualTo(550_000);
        assertThat(result.burdenRatePercent()).isEqualByComparingTo(new BigDecimal("21.82"));
        assertThat(result.unavailableReason()).isNull();
        assertThat(result.periodStart()).isEqualTo(today);
        assertThat(result.periodEnd()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(result.scheduledAmount()).isEqualTo(120000);
        assertThat(result.installmentCount()).isEqualTo(1);
    }

    @Test
    void futureMonthCoversWholeMonthAndZeroScheduleIsZeroRate() {
        var result = calculator.calculate(schedule("2026-11", 0, 0, true),
                basis(1_000_000L, 0, 0, 0), today);

        assertThat(result.periodStart()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(result.periodEnd()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(result.burdenRatePercent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.unavailableReason()).isNull();
    }

    @Test
    void incompleteScheduleWinsOverMissingAvailableAmount() {
        var result = calculator.calculate(schedule("2026-09", 0, 0, false),
                basis(null, 0, 0, 0), today);

        assertThat(result.unavailableReason()).isEqualTo(InstallmentBurdenResponse.INCOMPLETE_SCHEDULE);
        assertThat(result.burdenRatePercent()).isNull();
        assertThat(result.availableAmount()).isNull();
        assertThat(result.complete()).isFalse();
        assertThat(result.exclusions()).extracting(InstallmentExclusionResponse::reason)
                .containsExactly(InstallmentExclusionReason.OVERDUE);
    }

    @Test
    void missingIncomeLeavesAvailableAmountNull() {
        var result = calculator.calculate(schedule("2026-09", 120000, 1, true),
                basis(null, 300_000, 0, 0), today);

        assertThat(result.unavailableReason()).isEqualTo(InstallmentBurdenResponse.AVAILABLE_AMOUNT_UNAVAILABLE);
        assertThat(result.availableAmount()).isNull();
        assertThat(result.burdenRatePercent()).isNull();
        assertThat(result.availableAmountBasis().notes()).containsExactly(AvailableAmountBasis.INCOME_UNKNOWN);
    }

    @Test
    void nonPositiveAvailableAmountIsReportedWithTheAmount() {
        var zero = calculator.calculate(schedule("2026-09", 120000, 1, true),
                basis(1_000_000L, 600_000, 400_000, 0), today);
        var negative = calculator.calculate(schedule("2026-09", 120000, 1, true),
                basis(1_000_000L, 600_000, 400_000, 1), today);

        assertThat(zero.availableAmount()).isZero();
        assertThat(zero.unavailableReason()).isEqualTo(InstallmentBurdenResponse.NON_POSITIVE_AVAILABLE_AMOUNT);
        assertThat(zero.burdenRatePercent()).isNull();
        assertThat(negative.availableAmount()).isEqualTo(-1);
        assertThat(negative.unavailableReason()).isEqualTo(InstallmentBurdenResponse.NON_POSITIVE_AVAILABLE_AMOUNT);
    }

    @Test
    void roundsHalfUpToTwoDecimals() {
        // 100000 / 300000 * 100 = 33.333… → 33.33, 200000 / 300000 * 100 = 66.666… → 66.67
        assertThat(calculator.calculate(schedule("2026-09", 100_000, 1, true), basis(300_000L, 0, 0, 0), today)
                .burdenRatePercent()).isEqualByComparingTo(new BigDecimal("33.33"));
        assertThat(calculator.calculate(schedule("2026-09", 200_000, 2, true), basis(300_000L, 0, 0, 0), today)
                .burdenRatePercent()).isEqualByComparingTo(new BigDecimal("66.67"));
    }

    private InstallmentScheduleResponse schedule(String month, long amount, long count, boolean complete) {
        List<InstallmentExclusionResponse> exclusions = complete
                ? List.of()
                : List.of(new InstallmentExclusionResponse(InstallmentExclusionReason.OVERDUE, 1));
        return new InstallmentScheduleResponse(today, month, month, complete, exclusions.size(), exclusions,
                List.of(new InstallmentMonthResponse(month, amount, count)));
    }

    private AvailableAmountBasis basis(Long income, long fixed, long saving, long loan) {
        List<String> notes = income == null ? List.of(AvailableAmountBasis.INCOME_UNKNOWN) : List.of();
        return new AvailableAmountBasis(income, fixed, saving, loan, notes);
    }
}
