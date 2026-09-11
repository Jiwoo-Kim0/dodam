package com.ssafy.dodam.domain.cashflow.service;

import static com.ssafy.dodam.domain.cashflow.dto.CashflowPlannedEvent.Direction.OUTFLOW;
import static com.ssafy.dodam.domain.loan.repository.LoanSpecifications.ownedBy;

import com.ssafy.dodam.domain.card.repository.CardBillingStatementReader;
import com.ssafy.dodam.domain.cashflow.dto.CashflowEventCollection;
import com.ssafy.dodam.domain.cashflow.dto.CashflowPlannedEvent;
import com.ssafy.dodam.domain.cashflow.dto.CashflowPlannedEvent.Direction;
import com.ssafy.dodam.domain.cashflow.repository.CashflowSourceReader;
import com.ssafy.dodam.domain.cashflow.repository.CashflowSourceReader.Repayment;
import com.ssafy.dodam.domain.loan.repository.LoanRepository;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentScheduleCalculator;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentScheduleItem;
import com.ssafy.dodam.domain.savings.repository.SavingsQueryRepository;
import com.ssafy.dodam.domain.savings.service.SavingsScheduleCalculator;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.policy.PaymentSchedulePeriodPolicy;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 539: 본인 예정 이벤트를 모은다. 예측 실행·잔액 계산·저장은 540·541의 책임이다. */
@Service
@Transactional(readOnly = true)
public class CashflowEventCollector {
    private final CardBillingStatementReader cards;
    private final LoanRepository loans;
    private final LoanRepaymentScheduleCalculator loanSchedules;
    private final SavingsQueryRepository savings;
    private final SavingsScheduleCalculator savingSchedules;
    private final CashflowSourceReader sources;
    private final List<AccountScheduledEventSource> accountSources;
    private final PaymentSchedulePeriodPolicy periods;

    public CashflowEventCollector(CardBillingStatementReader cards, LoanRepository loans,
            LoanRepaymentScheduleCalculator loanSchedules, SavingsQueryRepository savings,
            SavingsScheduleCalculator savingSchedules, CashflowSourceReader sources,
            List<AccountScheduledEventSource> accountSources, PaymentSchedulePeriodPolicy periods) {
        this.cards = cards;
        this.loans = loans;
        this.loanSchedules = loanSchedules;
        this.savings = savings;
        this.savingSchedules = savingSchedules;
        this.sources = sources;
        this.accountSources = List.copyOf(accountSources);
        this.periods = periods;
    }

    public CashflowEventCollection collect(Long userId, LocalDate baseDate, LocalDate endDate) {
        if (userId == null || userId <= 0 || baseDate == null || endDate == null) {
            throw new InvalidRequestException("사용자와 기준일·종료일을 지정해야 합니다.");
        }
        var period = periods.resolve(baseDate, endDate);
        List<CashflowPlannedEvent> events = new ArrayList<>();
        TreeSet<String> reasons = new TreeSet<>();

        for (var row : cards.readScheduled(userId, period)) {
            if ("SCHEDULED".equals(row.status()) && row.scheduledAmount() > 0) {
                events.add(event(userId, row.billingDate(), "CARD_PAYMENT", "카드대금", OUTFLOW,
                        row.scheduledAmount(), "CARD_BILLING", row.cardId().toString(), row.billingMonth()));
            }
        }
        collectLoans(userId, baseDate, endDate, events, reasons);
        var schedule = savingSchedules.calculate(savings.findAllOwned(userId), savings.findPayments(userId),
                baseDate, endDate, baseDate);
        schedule.reasons().forEach(reason -> reasons.add("SAVINGS_" + reason));
        for (var row : schedule.schedules()) {
            if (row.amount() <= 0) {
                reasons.add("SAVINGS_INVALID_AMOUNT");
                continue;
            }
            boolean stored = row.paymentId() != null;
            events.add(event(userId, row.paymentDate(), "SAVINGS_PAYMENT", "적금 납입", OUTFLOW,
                    row.amount(), stored ? "SAVINGS_PAYMENT" : "SAVINGS_ACCOUNT",
                    stored ? row.paymentId().toString() : row.accountId().toString(),
                    stored ? "stored" : YearMonth.from(row.paymentDate()).toString()));
        }
        collectSubscriptions(userId, baseDate, endDate, events, reasons);
        if (accountSources.isEmpty()) {
            reasons.add("ACCOUNT_SCHEDULE_SOURCE_UNAVAILABLE");
        }
        for (var source : accountSources) {
            for (var row : source.read(userId, baseDate, endDate)) {
                if (!userId.equals(row.userId())) {
                    throw new IllegalStateException("계좌 예정 거래 소유권이 일치하지 않습니다.");
                }
                events.add(event(userId, row.date(), row.type(), row.name(), row.direction(), row.amount(),
                        row.sourceType(), row.sourceId(), row.occurrence()));
            }
        }
        Map<String, CashflowPlannedEvent> unique = new LinkedHashMap<>();
        for (var item : events) {
            if (item.eventDate().isBefore(baseDate) || item.eventDate().isAfter(endDate)) {
                continue;
            }
            var previous = unique.putIfAbsent(item.dedupeKey(), item);
            if (previous != null && (!previous.eventDate().equals(item.eventDate())
                    || previous.amount() != item.amount() || previous.direction() != item.direction())) {
                // 원본 간 충돌을 조용히 합치면 확정되지 않은 금액을 예측에 사용하게 된다.
                throw new InvalidRequestException("동일 예정 이벤트의 날짜·금액·방향이 일치하지 않습니다.");
            }
        }
        return new CashflowEventCollection(unique.values().stream()
                .sorted(Comparator.comparing(CashflowPlannedEvent::eventDate)
                        .thenComparing(CashflowPlannedEvent::dedupeKey)).toList(), List.copyOf(reasons));
    }

    private void collectLoans(Long userId, LocalDate start, LocalDate end,
            List<CashflowPlannedEvent> events, TreeSet<String> reasons) {
        var repayments = sources.repayments(userId);
        for (var loan : loans.findAll(ownedBy(userId))) {
            if (!loan.getStatus().isOutstanding()) {
                continue;
            }
            var stored = repayments.stream().filter(row -> row.loanId() == loan.getId()).toList();
            for (var row : stored) {
                if ("SCHEDULED".equals(row.status())) {
                    events.add(event(userId, row.date(), "LOAN_REPAYMENT", "대출 상환", OUTFLOW,
                            row.amount(), "LOAN_REPAYMENT", Long.toString(row.id()), "stored"));
                }
                if ((!"PAID".equals(row.status()) && row.date().isBefore(start))
                        || (("FAILED".equals(row.status()) || "OVERDUE".equals(row.status()))
                        && !row.date().isAfter(end))) {
                    reasons.add("LOAN_UNSETTLED_REPAYMENT");
                }
            }
            // 현재 잔액에서 과거 회차 원금을 다시 차감하면 기간 내 남은 상환액이 줄어든다.
            // 532의 STALE_REPAYMENT_DATE 정책과 같이 추정만 중단하고 저장된 예정은 보존한다.
            if (loan.getNextRepaymentDate() != null && loan.getNextRepaymentDate().isBefore(start)) {
                reasons.add("LOAN_STALE_REPAYMENT_DATE");
                continue;
            }
            var schedule = loanSchedules.calculate(loan, start);
            if (schedule.truncated()) {
                reasons.add("LOAN_SCHEDULE_TRUNCATED");
            }
            if (loan.getBalance() > 0 && schedule.items().isEmpty()) {
                reasons.add("LOAN_SCHEDULE_UNAVAILABLE");
            }
            // 금액이나 회차가 다르면 후속 계산의 원금 잔액도 달라진다. 원금·이자 매칭 근거 없이
            // 저장 총액만 치환하지 않고 알려진 저장 일정만 남긴다.
            var periodSchedule = schedule.items().stream()
                    .filter(row -> !row.repaymentDate().isBefore(start) && !row.repaymentDate().isAfter(end))
                    .toList();
            if (requiresReconciliation(stored, periodSchedule, start, end)) {
                reasons.add("LOAN_REPAYMENT_RECONCILIATION_REQUIRED");
                continue;
            }
            // 저장 회차와 일치함을 확인한 월은 추정 회차를 중복 반영하지 않는다.
            var storedMonths = stored.stream().map(row -> YearMonth.from(row.date())).toList();
            for (var row : periodSchedule) {
                if (row.amount() > 0 && !storedMonths.contains(YearMonth.from(row.repaymentDate()))) {
                    events.add(event(userId, row.repaymentDate(), "LOAN_REPAYMENT", "대출 상환", OUTFLOW,
                            row.amount(), "LOAN", loan.getId().toString(),
                            YearMonth.from(row.repaymentDate()).toString()));
                }
            }
        }
    }

    private boolean requiresReconciliation(List<Repayment> stored,
            List<LoanRepaymentScheduleItem> schedule, LocalDate start, LocalDate end) {
        for (var item : schedule) {
            var sameMonth = stored.stream()
                    .filter(row -> YearMonth.from(row.date()).equals(YearMonth.from(item.repaymentDate())))
                    .toList();
            if (!sameMonth.isEmpty() && (sameMonth.size() != 1
                    || !"SCHEDULED".equals(sameMonth.getFirst().status())
                    || !sameMonth.getFirst().date().equals(item.repaymentDate())
                    || sameMonth.getFirst().amount() != item.amount())) {
                return true;
            }
        }
        // 계산에서 찾을 수 없는 저장 예정도 같은 대출의 잔액에 영향을 줄 수 있다.
        return stored.stream().filter(row -> "SCHEDULED".equals(row.status()))
                .filter(row -> !row.date().isBefore(start) && !row.date().isAfter(end))
                .anyMatch(row -> schedule.stream().noneMatch(item -> item.repaymentDate().equals(row.date())));
    }

    private void collectSubscriptions(Long userId, LocalDate start, LocalDate end,
            List<CashflowPlannedEvent> events, TreeSet<String> reasons) {
        var subscriptions = sources.subscriptions(userId);
        if (!subscriptions.available()) {
            reasons.add("SUBSCRIPTION_SOURCE_UNAVAILABLE");
        }
        for (var row : subscriptions.rows()) {
            // 구독의 유효 기간과 이번 조회가 겹치지 않으면 대사·입력 상태도 이번 결과와 무관하다.
            if ((row.endDate() != null && row.endDate().isBefore(start))
                    || (row.nextDate() != null && row.nextDate().isAfter(end))) {
                continue;
            }
            if (row.cardId() != null) {
                // 구독 승인일과 카드 출금일은 다르다. 청구서 포함 여부를 입증할 연결 정보가 없다.
                reasons.add("CARD_SUBSCRIPTION_REQUIRES_BILLING_RECONCILIATION");
                continue;
            }
            if (row.nextDate() == null || row.nextDate().isBefore(start)) {
                reasons.add("SUBSCRIPTION_NEXT_DATE_UNAVAILABLE_OR_STALE");
                continue;
            }
            if (!List.of("WEEKLY", "MONTHLY", "YEARLY").contains(row.cycle())
                    || row.amount() <= 0 || (row.paymentDay() != null
                    && (row.paymentDay() < 1 || row.paymentDay() > 31))) {
                reasons.add("SUBSCRIPTION_INVALID_TERMS");
                continue;
            }
            LocalDate anchor = row.nextDate();
            LocalDate date = anchor;
            for (int index = 0; !date.isAfter(end)
                    && (row.endDate() == null || !date.isAfter(row.endDate())); index++) {
                events.add(event(userId, date, "SUBSCRIPTION_PAYMENT", "정기결제", OUTFLOW,
                        row.amount(), "SUBSCRIPTION", row.id(), date.toString()));
                int next = index + 1;
                // 직전 보정일이 아닌 원래 계약일을 사용해 1/31 → 2/28 → 3/31을 유지한다.
                date = switch (row.cycle()) {
                    case "WEEKLY" -> anchor.plusWeeks(next);
                    case "YEARLY" -> anchor.plusYears(next);
                    default -> {
                        YearMonth month = YearMonth.from(anchor).plusMonths(next);
                        int day = row.paymentDay() == null ? anchor.getDayOfMonth() : row.paymentDay();
                        yield month.atDay(Math.min(day, month.lengthOfMonth()));
                    }
                };
            }
        }
    }

    private static CashflowPlannedEvent event(Long userId, LocalDate date, String type, String name,
            Direction direction, long amount, String sourceType, String sourceId, String occurrence) {
        return new CashflowPlannedEvent(date, type, name, direction, amount, sourceType, sourceId,
                CashflowEventKeys.key(userId, sourceType, sourceId, occurrence));
    }
}
