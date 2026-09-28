package com.ssafy.dodam.domain.cashflow.service;

import static com.ssafy.dodam.domain.cashflow.dto.CashflowPlannedEvent.Direction.INFLOW;
import static com.ssafy.dodam.domain.cashflow.dto.CashflowPlannedEvent.Direction.OUTFLOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssafy.dodam.domain.card.repository.CardBillingStatementReader;
import com.ssafy.dodam.domain.card.repository.CardPaymentScheduleRow;
import com.ssafy.dodam.domain.cashflow.repository.CashflowSourceReader;
import com.ssafy.dodam.domain.cashflow.repository.CashflowSourceReader.Repayment;
import com.ssafy.dodam.domain.cashflow.repository.CashflowSourceReader.Subscription;
import com.ssafy.dodam.domain.cashflow.repository.CashflowSourceReader.SubscriptionRows;
import com.ssafy.dodam.domain.loan.entity.Loan;
import com.ssafy.dodam.domain.loan.entity.LoanStatus;
import com.ssafy.dodam.domain.loan.repository.LoanRepository;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentSchedule;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentScheduleCalculator;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentScheduleItem;
import com.ssafy.dodam.domain.savings.repository.SavingsAccountRow;
import com.ssafy.dodam.domain.savings.repository.SavingsPaymentRow;
import com.ssafy.dodam.domain.savings.repository.SavingsQueryRepository;
import com.ssafy.dodam.domain.savings.service.SavingsScheduleCalculator;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.policy.PaymentSchedulePeriodPolicy;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

class CashflowEventCollectorTest {
    private final LocalDate start = LocalDate.of(2026, 1, 1);
    private final LocalDate end = LocalDate.of(2026, 3, 31);
    private final CardBillingStatementReader cards = mock(CardBillingStatementReader.class);
    private final LoanRepository loans = mock(LoanRepository.class);
    private final LoanRepaymentScheduleCalculator loanSchedules = mock(LoanRepaymentScheduleCalculator.class);
    private final SavingsQueryRepository savings = mock(SavingsQueryRepository.class);
    private final CashflowSourceReader sources = mock(CashflowSourceReader.class);
    private final AccountScheduledEventSource accounts = mock(AccountScheduledEventSource.class);
    private CashflowEventCollector collector;

    @BeforeEach
    void setup() {
        when(sources.subscriptions(1L)).thenReturn(new SubscriptionRows(true, List.of()));
        collector = collector(List.of(accounts));
    }

    private CashflowEventCollector collector(List<AccountScheduledEventSource> accountSources) {
        return new CashflowEventCollector(cards, loans, loanSchedules, savings,
                new SavingsScheduleCalculator(), sources, accountSources, new PaymentSchedulePeriodPolicy());
    }

    @Test
    void emptySourcesAreCompleteButMissingSourcesAreNot() {
        assertThat(collector.collect(1L, start, end).complete()).isTrue();
        when(sources.subscriptions(1L)).thenReturn(new SubscriptionRows(false, List.of()));
        var result = collector(List.of()).collect(1L, start, end);
        assertThat(result.events()).isEmpty();
        assertThat(result.complete()).isFalse();
        assertThat(result.reasons()).containsExactly(
                "ACCOUNT_SCHEDULE_SOURCE_UNAVAILABLE", "SUBSCRIPTION_SOURCE_UNAVAILABLE");
    }

    @Test
    void monthlySubscriptionRetainsMonthEndAndEndDateIsInclusive() {
        when(sources.subscriptions(1L)).thenReturn(new SubscriptionRows(true, List.of(
                new Subscription("sub-1", null, 100L, "MONTHLY", 31,
                        LocalDate.of(2026, 1, 31), end))));
        assertThat(collector.collect(1L, start, end).events()).extracting(event -> event.eventDate())
                .containsExactly(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), end);
    }

    @Test
    void cardSubscriptionIsNotAddedToCardBill() {
        when(cards.readScheduled(eq(1L), any())).thenReturn(List.of(
                new CardPaymentScheduleRow(7L, "masked", "name", "2026-01", start, 1000L, "SCHEDULED")));
        when(sources.subscriptions(1L)).thenReturn(new SubscriptionRows(true, List.of(
                new Subscription("sub-1", 7L, 100L, "MONTHLY", 1, start, end))));
        var result = collector.collect(1L, start, end);
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().getFirst().amount()).isEqualTo(1000);
        assertThat(result.reasons()).contains("CARD_SUBSCRIPTION_REQUIRES_BILLING_RECONCILIATION");
    }

    @Test
    void sameOriginAcrossAccountAndCardIsOnlyCountedOnce() {
        when(cards.readScheduled(eq(1L), any())).thenReturn(List.of(
                new CardPaymentScheduleRow(7L, "masked", "name", "2026-01", start, 1000L, "SCHEDULED")));
        when(accounts.read(1L, start, end)).thenReturn(List.of(
                new AccountScheduledEventSource.AccountEvent(1L, start, "CARD_PAYMENT", "출금", OUTFLOW,
                        1000L, "CARD_BILLING", "7", "2026-01")));
        assertThat(collector.collect(1L, start, end).events()).hasSize(1);
    }

    @Test
    void equalDateAndAmountWithDifferentOriginsRemainSeparateAndIncomeDirectionIsKept() {
        when(accounts.read(1L, start, end)).thenReturn(List.of(
                account(1L, start, "one", 100L), account(1L, start, "two", 100L),
                account(1L, end.plusDays(1), "outside", 100L)));
        var result = collector.collect(1L, start, end);
        assertThat(result.events()).hasSize(2).allMatch(event -> event.direction() == INFLOW);
    }

    @Test
    void conflictingSameOriginFailsInsteadOfSilentlyPickingAmount() {
        when(accounts.read(1L, start, end)).thenReturn(List.of(
                account(1L, start, "one", 100L), account(1L, start, "one", 200L)));
        assertThatThrownBy(() -> collector.collect(1L, start, end)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsForeignAccountSourceRows() {
        when(accounts.read(1L, start, end)).thenReturn(List.of(account(2L, start, "one", 100L)));
        assertThatThrownBy(() -> collector.collect(1L, start, end)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validatesRangeBeforeReadingSources() {
        assertThatThrownBy(() -> collector.collect(1L, end, start)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> collector.collect(1L, start, start.plusYears(1)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> collector.collect(null, start, end)).isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(cards, loans, savings, accounts);
    }

    @Test
    void savedLoanInstallmentsSuppressEstimatesIncludingPaidAndFailedMonths() {
        Loan loan = mock(Loan.class);
        when(loan.getId()).thenReturn(9L);
        when(loan.getStatus()).thenReturn(LoanStatus.ACTIVE);
        when(loan.getBalance()).thenReturn(1000L);
        when(loans.findAll(org.mockito.ArgumentMatchers.<Specification<Loan>>any())).thenReturn(List.of(loan));
        when(sources.repayments(1L)).thenReturn(List.of(
                new Repayment(9L, 1L, start, 100L, "SCHEDULED"),
                new Repayment(9L, 2L, start.plusMonths(1), 100L, "PAID"),
                new Repayment(9L, 3L, start.plusMonths(2), 100L, "FAILED")));
        when(loanSchedules.calculate(loan, start)).thenReturn(new LoanRepaymentSchedule(List.of(
                new LoanRepaymentScheduleItem(1, start, 150, 100, 50, 900),
                new LoanRepaymentScheduleItem(2, start.plusMonths(1), 150, 100, 50, 800),
                new LoanRepaymentScheduleItem(3, start.plusMonths(2), 150, 100, 50, 700)), false));
        var result = collector.collect(1L, start, end);
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().getFirst().amount()).isEqualTo(100);
        assertThat(result.reasons()).contains("LOAN_UNSETTLED_REPAYMENT");
    }

    @Test
    void savingsUsesSavedScheduleAndDoesNotResurrectPaidOrFailedInstallments() {
        when(savings.findAllOwned(1L)).thenReturn(List.of(new SavingsAccountRow(
                8L, "적금", "masked", "001", "은행", "ACTIVE", 100L, true,
                java.math.BigDecimal.ONE, 200L, 1, start, start.minusYears(1), end.plusYears(1), null)));
        when(savings.findPayments(1L)).thenReturn(List.of(
                new SavingsPaymentRow(1L, 8L, start, 100, 1, "SCHEDULED"),
                new SavingsPaymentRow(2L, 8L, start.plusMonths(1), 200, 2, "PAID"),
                new SavingsPaymentRow(3L, 8L, start.plusMonths(2), 200, 3, "FAILED")));
        var result = collector.collect(1L, start, end);
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().getFirst().amount()).isEqualTo(100);
        assertThat(result.events().getFirst().sourceType()).isEqualTo("SAVINGS_PAYMENT");
    }

    @Test
    void weeklyAndYearlySchedulesHonorTheirAnchor() {
        when(sources.subscriptions(1L)).thenReturn(new SubscriptionRows(true, List.of(
                new Subscription("weekly", null, 100L, "WEEKLY", null, start, start.plusWeeks(2)),
                new Subscription("yearly", null, 200L, "YEARLY", null, start, null))));
        var result = collector.collect(1L, start, end);
        assertThat(result.events()).filteredOn(event -> event.sourceId().equals("weekly"))
                .extracting(event -> event.eventDate()).containsExactly(start, start.plusWeeks(1), start.plusWeeks(2));
        assertThat(result.events()).filteredOn(event -> event.sourceId().equals("yearly")).hasSize(1);
    }

    @Test
    void staleSubscriptionIsNotProjectedAsIfAlreadySettled() {
        when(sources.subscriptions(1L)).thenReturn(new SubscriptionRows(true, List.of(
                new Subscription("old", null, 100L, "MONTHLY", 1, start.minusMonths(1), null))));
        var result = collector.collect(1L, start, end);
        assertThat(result.events()).isEmpty();
        assertThat(result.reasons()).contains("SUBSCRIPTION_NEXT_DATE_UNAVAILABLE_OR_STALE");
    }

    @Test
    void keysAreStableUserScopedAndLengthFramingAvoidsDelimiterCollision() {
        assertThat(CashflowEventKeys.key(1L, "ACCOUNT", "1", "2026-01"))
                .hasSize(64).isEqualTo(CashflowEventKeys.key(1L, "ACCOUNT", "1", "2026-01"))
                .isNotEqualTo(CashflowEventKeys.key(2L, "ACCOUNT", "1", "2026-01"));
        assertThat(CashflowEventKeys.key(1L, "A|B", "C", "D"))
                .isNotEqualTo(CashflowEventKeys.key(1L, "A", "B|C", "D"));
    }

    private AccountScheduledEventSource.AccountEvent account(Long user, LocalDate date, String id, long amount) {
        return new AccountScheduledEventSource.AccountEvent(user, date, "ACCOUNT_INCOME", "예정 입금", INFLOW,
                amount, "ACCOUNT", id, "stored");
    }
}
