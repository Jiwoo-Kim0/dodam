package com.ssafy.dodam.domain.installment.service;

import com.ssafy.dodam.domain.installment.dto.response.InstallmentExclusionResponse;
import com.ssafy.dodam.domain.installment.dto.response.InstallmentMonthResponse;
import com.ssafy.dodam.domain.installment.dto.response.InstallmentScheduleResponse;
import com.ssafy.dodam.domain.installment.entity.InstallmentExclusionReason;
import com.ssafy.dodam.domain.installment.entity.InstallmentStatus;
import com.ssafy.dodam.domain.installment.repository.InstallmentRow;
import com.ssafy.dodam.global.policy.YearMonthPeriod;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class InstallmentScheduleCalculator {

    public InstallmentScheduleResponse calculate(
            List<InstallmentRow> rows, YearMonthPeriod period, LocalDate asOfDate
    ) {
        int monthCount = Math.toIntExact(
                ChronoUnit.MONTHS.between(period.startMonth(), period.endMonth()) + 1);
        long[] amounts = new long[monthCount];
        long[] counts = new long[monthCount];
        Map<InstallmentExclusionReason, Long> exclusions =
                new EnumMap<>(InstallmentExclusionReason.class);

        for (InstallmentRow row : rows) {
            if (row.status() == InstallmentStatus.COMPLETED || row.status() == InstallmentStatus.CANCELED) {
                continue;
            }
            InstallmentExclusionReason reason = exclusionReason(row, asOfDate);
            if (reason != null) {
                exclusions.merge(reason, 1L, Math::addExact);
                continue;
            }
            YearMonth nextMonth = YearMonth.from(row.nextPaymentDate());
            // 잔여 회차 전체를 펼치지 않고 최대 12개의 요청 월만 검사한다.
            for (int index = 0; index < monthCount; index++) {
                YearMonth month = period.startMonth().plusMonths(index);
                long offset = ChronoUnit.MONTHS.between(nextMonth, month);
                if (offset >= 0 && offset < row.remainingMonths()) {
                    LocalDate paymentDate = paymentDate(row.nextPaymentDate(), month);
                    if (!paymentDate.isBefore(asOfDate)) {
                        amounts[index] = Math.addExact(amounts[index], row.monthlyAmount());
                        counts[index] = Math.addExact(counts[index], 1L);
                    }
                }
            }
        }

        List<InstallmentMonthResponse> months = new ArrayList<>();
        for (int index = 0; index < monthCount; index++) {
            months.add(new InstallmentMonthResponse(
                    period.startMonth().plusMonths(index).toString(), amounts[index], counts[index]));
        }
        List<InstallmentExclusionResponse> reasons = exclusions.entrySet().stream()
                .map(entry -> new InstallmentExclusionResponse(entry.getKey(), entry.getValue()))
                .toList();
        long excludedCount = reasons.stream().mapToLong(InstallmentExclusionResponse::count).sum();
        return new InstallmentScheduleResponse(
                asOfDate, period.startMonth().toString(), period.endMonth().toString(),
                reasons.isEmpty(), excludedCount, reasons, months);
    }

    private InstallmentExclusionReason exclusionReason(InstallmentRow row, LocalDate asOfDate) {
        if (row.status() == InstallmentStatus.OVERDUE) {
            return InstallmentExclusionReason.OVERDUE;
        }
        if (row.remainingMonths() == 0) {
            return InstallmentExclusionReason.NO_REMAINING_PAYMENTS;
        }
        if (row.nextPaymentDate() == null) {
            return InstallmentExclusionReason.MISSING_NEXT_PAYMENT_DATE;
        }
        if (row.purchaseDate().isAfter(row.firstPaymentDate())
                || row.nextPaymentDate().isBefore(row.firstPaymentDate())) {
            return InstallmentExclusionReason.INCONSISTENT_PAYMENT_DATES;
        }
        if (row.nextPaymentDate().isBefore(asOfDate)) {
            return InstallmentExclusionReason.STALE_NEXT_PAYMENT_DATE;
        }
        return null;
    }

    // 짧은 달을 거친 뒤에도 다음 결제일의 원래 일자를 유지한다.
    LocalDate paymentDate(LocalDate nextPaymentDate, YearMonth month) {
        return month.atDay(Math.min(nextPaymentDate.getDayOfMonth(), month.lengthOfMonth()));
    }
}
