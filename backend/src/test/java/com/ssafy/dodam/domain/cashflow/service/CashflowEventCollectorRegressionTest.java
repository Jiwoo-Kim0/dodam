package com.ssafy.dodam.domain.cashflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.ssafy.dodam.domain.card.repository.CardBillingStatementReader;
import com.ssafy.dodam.domain.cashflow.repository.CashflowSourceReader;
import com.ssafy.dodam.domain.cashflow.repository.CashflowSourceReader.Repayment;
import com.ssafy.dodam.domain.cashflow.repository.CashflowSourceReader.Subscription;
import com.ssafy.dodam.domain.cashflow.repository.CashflowSourceReader.SubscriptionRows;
import com.ssafy.dodam.domain.cashflow.service.AccountScheduledEventSource;
import com.ssafy.dodam.domain.cashflow.service.CashflowEventCollector;
import com.ssafy.dodam.domain.loan.entity.Loan;
import com.ssafy.dodam.domain.loan.entity.LoanStatus;
import com.ssafy.dodam.domain.loan.entity.RepaymentType;
import com.ssafy.dodam.domain.loan.repository.LoanRepository;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentScheduleCalculator;
import com.ssafy.dodam.domain.savings.repository.SavingsQueryRepository;
import com.ssafy.dodam.domain.savings.service.SavingsScheduleCalculator;
import com.ssafy.dodam.global.policy.PaymentSchedulePeriodPolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

class CashflowEventCollectorRegressionTest {
    private final LoanRepository loans = mock(LoanRepository.class);
    private final CashflowSourceReader sources = mock(CashflowSourceReader.class);
    private CashflowEventCollector collector;

    @BeforeEach
    void setup() {
        when(sources.subscriptions(1L)).thenReturn(new SubscriptionRows(true, List.of()));
        collector = new CashflowEventCollector(mock(CardBillingStatementReader.class), loans,
                new LoanRepaymentScheduleCalculator(), mock(SavingsQueryRepository.class),
                new SavingsScheduleCalculator(), sources, List.of(mock(AccountScheduledEventSource.class)),
                new PaymentSchedulePeriodPolicy());
    }

    @Test
    void staleNextRepaymentMustNotClaimCompleteAfterDroppingPrincipal() {
        loan(1200L, "2026-08-01", "2026-11-01");
        var result = collector.collect(1L, LocalDate.parse("2026-09-10"), LocalDate.parse("2026-11-01"));
        assertThat(result.events()).isEmpty();
        assertThat(result.reasons()).contains("LOAN_STALE_REPAYMENT_DATE");
        assertThat(result.complete()).isFalse();
    }

    @Test
    void replacingStoredAmountMustReconcileFuturePrincipalOrMarkIncomplete() {
        loan(1000L, "2026-01-01", "2026-02-01");
        when(sources.repayments(1L)).thenReturn(List.of(
                new Repayment(9L, 1L, LocalDate.parse("2026-01-01"), 100L, "SCHEDULED")));
        var result = collector.collect(1L, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-02-01"));
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().getFirst().amount()).isEqualTo(100);
        assertThat(result.reasons()).contains("LOAN_REPAYMENT_RECONCILIATION_REQUIRED");
        assertThat(result.complete()).isFalse();
    }

    @Test
    void futureCardSubscriptionOutsideHorizonMustNotInvalidateEmptyPeriod() {
        when(sources.subscriptions(1L)).thenReturn(new SubscriptionRows(true, List.of(
                new Subscription("future", 7L, 100L, "MONTHLY", 1,
                        LocalDate.parse("2026-12-01"), null))));
        var result = collector.collect(1L, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"));
        assertThat(result.events()).isEmpty();
        assertThat(result.complete()).as("December subscription has no January cashflow").isTrue();
    }

    @Test
    void matchingStoredInstallmentKeepsRemainingPrincipal() {
        loan(1000L, "2026-01-01", "2026-02-01");
        when(sources.repayments(1L)).thenReturn(List.of(
                new Repayment(9L, 1L, LocalDate.parse("2026-01-01"), 500L, "SCHEDULED")));
        var result = collector.collect(1L, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-02-01"));
        assertThat(result.events()).hasSize(2);
        assertThat(result.events().stream().mapToLong(event -> event.amount()).sum()).isEqualTo(1000);
        assertThat(result.complete()).isTrue();
    }

    @Test
    void mismatchedInstallmentOutsideHorizonDoesNotInvalidateCurrentPeriod() {
        loan(1000L, "2026-01-01", "2026-02-01");
        when(sources.repayments(1L)).thenReturn(List.of(
                new Repayment(9L, 1L, LocalDate.parse("2026-02-01"), 100L, "SCHEDULED")));
        var result = collector.collect(1L, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"));
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().getFirst().amount()).isEqualTo(500);
        assertThat(result.complete()).isTrue();
    }

    @Test
    void staleLoanPreservesKnownStoredEvents() {
        loan(1200L, "2026-08-01", "2026-11-01");
        when(sources.repayments(1L)).thenReturn(List.of(
                new Repayment(9L, 1L, LocalDate.parse("2026-10-01"), 400L, "SCHEDULED")));
        var result = collector.collect(1L, LocalDate.parse("2026-09-10"), LocalDate.parse("2026-11-01"));
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().getFirst().amount()).isEqualTo(400);
        assertThat(result.reasons()).contains("LOAN_STALE_REPAYMENT_DATE");
    }

    @Test
    void shiftedStoredDateRequiresReconciliationEvenWithSameAmount() {
        loan(1000L, "2026-01-01", "2026-02-01");
        when(sources.repayments(1L)).thenReturn(List.of(
                new Repayment(9L, 1L, LocalDate.parse("2026-01-02"), 500L, "SCHEDULED")));
        var result = collector.collect(1L, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-02-01"));
        assertThat(result.events()).hasSize(1);
        assertThat(result.reasons()).contains("LOAN_REPAYMENT_RECONCILIATION_REQUIRED");
    }

    @Test
    void expiredSubscriptionDoesNotInvalidateCurrentPeriod() {
        when(sources.subscriptions(1L)).thenReturn(new SubscriptionRows(true, List.of(
                new Subscription("expired", 7L, 100L, "MONTHLY", 1,
                        LocalDate.parse("2025-12-01"), LocalDate.parse("2025-12-31")))));
        var result = collector.collect(1L, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"));
        assertThat(result.events()).isEmpty();
        assertThat(result.complete()).isTrue();
    }

    @Test
    void subscriptionAtPeriodEndStillRequiresReconciliation() {
        when(sources.subscriptions(1L)).thenReturn(new SubscriptionRows(true, List.of(
                new Subscription("boundary", 7L, 100L, "MONTHLY", 31,
                        LocalDate.parse("2026-01-31"), null))));
        var result = collector.collect(1L, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"));
        assertThat(result.reasons()).contains("CARD_SUBSCRIPTION_REQUIRES_BILLING_RECONCILIATION");
    }

    private void loan(long balance, String next, String maturity) {
        Loan loan = mock(Loan.class);
        when(loan.getId()).thenReturn(9L);
        when(loan.getStatus()).thenReturn(LoanStatus.ACTIVE);
        when(loan.getBalance()).thenReturn(balance);
        when(loan.getInterestRate()).thenReturn(BigDecimal.ZERO);
        when(loan.getRepaymentType()).thenReturn(RepaymentType.EQUAL_PRINCIPAL);
        when(loan.getRepaymentDay()).thenReturn(1);
        when(loan.getNextRepaymentDate()).thenReturn(LocalDate.parse(next));
        when(loan.getMaturityDate()).thenReturn(LocalDate.parse(maturity));
        when(loans.findAll(org.mockito.ArgumentMatchers.<Specification<Loan>>any())).thenReturn(List.of(loan));
    }
}
