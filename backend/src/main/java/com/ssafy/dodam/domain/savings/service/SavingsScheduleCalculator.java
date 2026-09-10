package com.ssafy.dodam.domain.savings.service;

import com.ssafy.dodam.domain.savings.dto.response.SavingsScheduleItemResponse;
import com.ssafy.dodam.domain.savings.dto.response.SavingsPaymentSchedulesResponse;
import com.ssafy.dodam.domain.savings.repository.SavingsAccountRow;
import com.ssafy.dodam.domain.savings.repository.SavingsPaymentRow;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SavingsScheduleCalculator {
    public SavingsPaymentSchedulesResponse calculate(
            List<SavingsAccountRow> accounts, List<SavingsPaymentRow> payments,
            LocalDate start, LocalDate end, LocalDate today
    ) {
        List<SavingsScheduleItemResponse> items = new ArrayList<>();
        Set<String> reasons = new TreeSet<>();
        Map<Long, List<SavingsPaymentRow>> byAccount = payments.stream()
                .collect(Collectors.groupingBy(SavingsPaymentRow::accountId));
        for (var account : accounts) {
            if (!"ACTIVE".equals(account.status())) {
                continue;
            }
            List<SavingsPaymentRow> rows = byAccount.getOrDefault(account.accountId(), List.of());
            for (var payment : rows) {
                if ("SCHEDULED".equals(payment.status()) && !payment.paymentDate().isBefore(start)
                        && !payment.paymentDate().isAfter(end)) {
                    items.add(new SavingsScheduleItemResponse(account.accountId(), account.accountNoMasked(),
                            payment.paymentId(), payment.paymentDate(), payment.amount(), "STORED_SCHEDULED"));
                    if (payment.paymentDate().isBefore(today)) {
                        reasons.add("OVERDUE_SCHEDULED_PAYMENT");
                    }
                }
            }
            if (end.isBefore(today)) {
                continue;
            }
            if (!account.detailsAvailable() || account.monthlyAmount() == null
                    || account.paymentDay() == null || account.nextPaymentDate() == null
                    || account.expiryDate() == null) {
                reasons.add("INCOMPLETE_SAVINGS_TERMS");
                continue;
            }
            if (!account.expiryDate().isAfter(today)) {
                continue;
            }
            LocalDate next = account.nextPaymentDate();
            if (next.isBefore(today)) {
                reasons.add("STALE_NEXT_PAYMENT_DATE");
                continue;
            }
            if (next.isBefore(account.openingDate()) || !next.isBefore(account.expiryDate())) {
                reasons.add("INVALID_PAYMENT_DATES");
                continue;
            }
            // 월납 계약이다. 상태에 관계없이 저장된 회차가 있는 월에는 약정액을 보충하지 않는다.
            // FAILED/SKIPPED를 예정으로 부활시키거나 PAID와 월 약정액을 중복 반영하지 않는다.
            Set<YearMonth> storedMonths = rows.stream().map(row -> YearMonth.from(row.paymentDate()))
                    .collect(Collectors.toSet());
            YearMonth firstMonth = YearMonth.from(next);
            YearMonth from = YearMonth.from(start.isAfter(today) ? start : today);
            if (from.isBefore(firstMonth)) {
                from = firstMonth;
            }
            for (YearMonth month = from; !month.isAfter(YearMonth.from(end)); month = month.plusMonths(1)) {
                LocalDate date = month.equals(firstMonth) ? next
                        : month.atDay(Math.min(account.paymentDay(), month.lengthOfMonth()));
                if (!date.isBefore(start) && !date.isBefore(today) && !date.isAfter(end)
                        && date.isBefore(account.expiryDate()) && !storedMonths.contains(month)) {
                    items.add(new SavingsScheduleItemResponse(account.accountId(), account.accountNoMasked(),
                            null, date, account.monthlyAmount(), "ESTIMATED_MONTHLY"));
                }
            }
        }
        items.sort(Comparator.comparing(SavingsScheduleItemResponse::paymentDate)
                .thenComparing(SavingsScheduleItemResponse::accountId)
                .thenComparing(SavingsScheduleItemResponse::paymentId, Comparator.nullsLast(Comparator.naturalOrder())));
        Long total = SavingsAmounts.sum(items, SavingsScheduleItemResponse::amount);
        if (total == null) {
            reasons.add("AMOUNT_OUT_OF_RANGE");
        }
        return new SavingsPaymentSchedulesResponse(today, start, end, reasons.isEmpty(),
                List.copyOf(reasons), total, List.copyOf(items));
    }
}

