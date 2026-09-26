package com.ssafy.dodam.domain.goal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ssafy.dodam.domain.account.entity.Account;
import com.ssafy.dodam.domain.account.entity.AccountType;
import com.ssafy.dodam.domain.goal.repository.JdbcGoalPaceReader;
import com.ssafy.dodam.domain.goal.repository.JdbcGoalPaceReader.DepositSavingTerms;
import com.ssafy.dodam.domain.goal.repository.SafeboxAccountRepository;
import com.ssafy.dodam.domain.goal.repository.SafeboxMovementRepository;
import com.ssafy.dodam.domain.goal.entity.SafeboxAccount;
import com.ssafy.dodam.domain.goal.service.GoalAllocationReader.AccountAllocation;
import com.ssafy.dodam.domain.goal.service.GoalAllocationReader.GoalAllocation;
import com.ssafy.dodam.domain.goal.service.GoalAllocationReader.LinkedAccount;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 저축속도 정의(694 {@link GoalSavingPaceReader}). 창은 기준일이 든 달을 뺀 직전 3개 완료 월이다.
 */
class GoalSavingPaceReaderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 22);
    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 31);
    private static final long USER = 7L;

    private JdbcGoalPaceReader jdbc;
    private SafeboxAccountRepository safeboxAccounts;
    private SafeboxMovementRepository movements;
    private GoalSavingPaceReader reader;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcGoalPaceReader.class);
        safeboxAccounts = mock(SafeboxAccountRepository.class);
        movements = mock(SafeboxMovementRepository.class);
        reader = new GoalSavingPaceReader(jdbc, safeboxAccounts, movements);
        given(jdbc.readPaidPaymentTotals(anyCollection(), any(), any())).willReturn(Map.of());
        given(jdbc.readTerms(anyCollection())).willReturn(Map.of());
        given(jdbc.readFirstTransactionDate(eq(USER), anyCollection())).willReturn(Optional.empty());
    }

    @Test
    void savingsPaceIsPaidPaymentsOverTheWindowRegardlessOfBalanceOrGoalAge() {
        // 잔액 1,746만 원짜리 적금을 연결해도 속도는 창 안의 납입(월 40만 × 3)이다
        given(jdbc.readPaidPaymentTotals(eq(List.of(21L)), eq(FROM), eq(TO))).willReturn(Map.of(21L, 1_200_000L));

        assertThat(reader.monthlyPace(USER, allocation(savings(21L, 17_460_000L)), TODAY)).isEqualTo(400_000L);
        verify(jdbc, never()).readNetCashflowByAccount(any(), anyCollection(), any(), any());
    }

    @Test
    void savingsOpenedInsideTheWindowDividesByCoveredMonthsOnly() {
        // 7월 가입, 7·8월 납입 2회 → 2개월로 나눈다
        given(jdbc.readPaidPaymentTotals(eq(List.of(21L)), eq(FROM), eq(TO))).willReturn(Map.of(21L, 600_000L));
        given(jdbc.readTerms(List.of(21L))).willReturn(Map.of(21L,
                new DepositSavingTerms(300_000L, LocalDate.of(2026, 7, 10), LocalDate.of(2028, 7, 10))));

        assertThat(reader.monthlyPace(USER, allocation(savings(21L, 600_000L)), TODAY)).isEqualTo(300_000L);
    }

    @Test
    void savingsOpenedAfterTheWindowUsesContractAmountUntilFirstPayment() {
        // 9월 가입, 아직 납입 없음 → 약정 월 30만. 창 안에 가입했는데 납입이 없으면 0, 만기가 지났으면 0
        given(jdbc.readTerms(List.of(21L))).willReturn(Map.of(21L,
                new DepositSavingTerms(300_000L, LocalDate.of(2026, 9, 3), LocalDate.of(2028, 9, 3))));
        assertThat(reader.monthlyPace(USER, allocation(savings(21L, 0L)), TODAY)).isEqualTo(300_000L);

        given(jdbc.readTerms(List.of(21L))).willReturn(Map.of(21L,
                new DepositSavingTerms(300_000L, LocalDate.of(2026, 7, 3), LocalDate.of(2028, 7, 3))));
        assertThat(reader.monthlyPace(USER, allocation(savings(21L, 0L)), TODAY)).isZero();

        given(jdbc.readTerms(List.of(21L))).willReturn(Map.of(21L,
                new DepositSavingTerms(500_000L, LocalDate.of(2021, 3, 15), LocalDate.of(2026, 3, 15))));
        assertThat(reader.monthlyPace(USER, allocation(savings(21L, 30_000_000L)), TODAY)).isZero();
    }

    @Test
    void demandDepositPaceIsLinkedAccountNetCashflowAndNegativeOffsetsSavings() {
        // 입출금 연결: 3개월 순현금흐름 159,537 → 월 53,179. 계좌 잔액·담은 금액은 속도에 들어가지 않는다
        given(jdbc.readFirstTransactionDate(USER, List.of(3L))).willReturn(Optional.of(LocalDate.of(2026, 3, 1)));
        given(jdbc.readNetCashflowByAccount(USER, List.of(3L), FROM, TO)).willReturn(Map.of(3L, 159_537L));
        assertThat(reader.monthlyPace(USER, allocation(demand(3L, 17_460_000L)), TODAY)).isEqualTo(53_179L);

        // 적금 40만을 붓지만 입출금은 월 10만씩 줄고 있다 → 30만. 합이 음수면 0
        given(jdbc.readPaidPaymentTotals(eq(List.of(21L)), eq(FROM), eq(TO))).willReturn(Map.of(21L, 1_200_000L));
        given(jdbc.readNetCashflowByAccount(USER, List.of(3L), FROM, TO)).willReturn(Map.of(3L, -300_000L));
        assertThat(reader.monthlyPace(USER, allocation(demand(3L, 1_000_000L), savings(21L, 5_000_000L)), TODAY))
                .isEqualTo(300_000L);
        given(jdbc.readNetCashflowByAccount(USER, List.of(3L), FROM, TO)).willReturn(Map.of(3L, -1_800_000L));
        assertThat(reader.monthlyPace(USER, allocation(demand(3L, 1_000_000L), savings(21L, 5_000_000L)), TODAY)).isZero();
    }

    @Test
    void demandDepositWithHistoryOnlyThisMonthHasNoPaceYet() {
        // 이번 달에 첫 거래 → 완료 월 이력이 없어 0. 8월에 시작했으면 8월 한 달로 나눈다
        given(jdbc.readFirstTransactionDate(USER, List.of(3L))).willReturn(Optional.of(LocalDate.of(2026, 9, 2)));
        given(jdbc.readNetCashflowByAccount(USER, List.of(3L), FROM, TO)).willReturn(Map.of());
        assertThat(reader.monthlyPace(USER, allocation(demand(3L, 500_000L)), TODAY)).isZero();

        given(jdbc.readFirstTransactionDate(USER, List.of(3L))).willReturn(Optional.of(LocalDate.of(2026, 8, 20)));
        given(jdbc.readNetCashflowByAccount(USER, List.of(3L), FROM, TO)).willReturn(Map.of(3L, 250_000L));
        assertThat(reader.monthlyPace(USER, allocation(demand(3L, 500_000L)), TODAY)).isEqualTo(250_000L);
    }

    @Test
    void demandDepositQueryReceivesOnlyLinkedDemandAccountIds() {
        List<Long> linkedDemandIds = List.of(3L, 5L);
        given(jdbc.readFirstTransactionDate(USER, linkedDemandIds)).willReturn(Optional.of(LocalDate.of(2026, 6, 1)));
        given(jdbc.readNetCashflowByAccount(USER, linkedDemandIds, FROM, TO)).willReturn(Map.of(3L, 400_000L, 5L, 200_000L));

        assertThat(reader.monthlyPace(USER,
                allocation(demand(3L, 500_000L), savings(4L, 300_000L), demand(5L, 200_000L)), TODAY))
                .isEqualTo(200_000L);
        verify(jdbc).readNetCashflowByAccount(USER, linkedDemandIds, FROM, TO);
    }

    @Test
    void sharedDemandDepositCountsOnlyThisGoalsShare() {
        // 703: 같은 통장을 두 목표가 담으면 순현금흐름도 몫만큼만 센다. 담은 금액이 없으면 기여액이 잔액 전체라 목표 수로 균등 분할된다
        given(jdbc.readFirstTransactionDate(USER, List.of(3L))).willReturn(Optional.of(LocalDate.of(2026, 6, 1)));
        given(jdbc.readNetCashflowByAccount(USER, List.of(3L), FROM, TO)).willReturn(Map.of(3L, 600_000L));

        // 600,000 × (1,000,000 / 2,000,000) ÷ 3개월
        assertThat(reader.monthlyPace(USER, allocation(sharedDemand(3L, 1_000_000L, 1_000_000L, 2_000_000L)), TODAY))
                .isEqualTo(100_000L);
        // 담은 금액이 3 : 1이면 몫도 3 : 1 — 600,000 × (300,000 / 400,000) ÷ 3개월
        assertThat(reader.monthlyPace(USER, allocation(sharedDemand(3L, 1_000_000L, 300_000L, 400_000L)), TODAY))
                .isEqualTo(150_000L);

        // 음수도 몫만큼만 상쇄한다 — 적금 월 40만에서 절반인 10만만 깎인다
        given(jdbc.readPaidPaymentTotals(eq(List.of(21L)), eq(FROM), eq(TO))).willReturn(Map.of(21L, 1_200_000L));
        given(jdbc.readNetCashflowByAccount(USER, List.of(3L), FROM, TO)).willReturn(Map.of(3L, -600_000L));
        assertThat(reader.monthlyPace(USER,
                allocation(sharedDemand(3L, 1_000_000L, 1_000_000L, 2_000_000L), savings(21L, 5_000_000L)), TODAY))
                .isEqualTo(300_000L);
    }

    @Test
    void unsharedDemandDepositKeepsTheWholeNetCashflow() {
        // 몫이 1이면 702까지와 같은 값이다. 총 할당액이 0인 계좌(잔액 0)는 나눌 기준이 없어 전액으로 본다
        given(jdbc.readFirstTransactionDate(USER, List.of(3L))).willReturn(Optional.of(LocalDate.of(2026, 6, 1)));
        given(jdbc.readNetCashflowByAccount(USER, List.of(3L), FROM, TO)).willReturn(Map.of(3L, 600_000L));

        assertThat(reader.monthlyPace(USER, allocation(demand(3L, 1_000_000L)), TODAY)).isEqualTo(200_000L);
        assertThat(reader.monthlyPace(USER, allocation(sharedDemand(3L, 0L, 0L, 0L)), TODAY)).isEqualTo(200_000L);
    }

    @Test
    void noLinkedAccountMeansNoPace() {
        assertThat(reader.monthlyPace(USER, null, TODAY)).isZero();
        assertThat(reader.monthlyPace(USER, new GoalAllocation(1L, List.of(), 0L), TODAY)).isZero();
    }

    @Test
    void dedicatedAccountUsesGoalMovementInsteadOfWholeAccountCashflow() {
        given(safeboxAccounts.findById(USER)).willReturn(Optional.of(new SafeboxAccount(USER, 3L,
                LocalDateTime.of(2026, 6, 1, 0, 0))));
        given(movements.sumDelta(eq(USER), eq(1L), any(), any())).willReturn(900_000L);

        assertThat(reader.monthlyPace(USER, allocation(sharedDemand(3L, 1_500_000L, 900_000L,
                1_500_000L)), TODAY)).isEqualTo(300_000L);
        verify(jdbc, never()).readNetCashflowByAccount(any(), anyCollection(), any(), any());
    }

    @Test
    void monthsCoveredCountsFromTheSourceStartMonth() {
        YearMonth first = YearMonth.of(2026, 6);
        YearMonth last = YearMonth.of(2026, 8);
        assertThat(GoalSavingPaceReader.monthsCovered(first, last, null)).isEqualTo(3);
        assertThat(GoalSavingPaceReader.monthsCovered(first, last, LocalDate.of(2025, 1, 1))).isEqualTo(3);
        assertThat(GoalSavingPaceReader.monthsCovered(first, last, LocalDate.of(2026, 6, 30))).isEqualTo(3);
        assertThat(GoalSavingPaceReader.monthsCovered(first, last, LocalDate.of(2026, 8, 1))).isEqualTo(1);
        assertThat(GoalSavingPaceReader.monthsCovered(first, last, LocalDate.of(2026, 9, 1))).isZero();
    }

    // ---------- fixtures ----------

    private static GoalAllocation allocation(LinkedAccount... accounts) {
        long secured = 0;
        for (LinkedAccount item : accounts) {
            secured += item.contribution();
        }
        return new GoalAllocation(1L, List.of(accounts), secured);
    }

    private static LinkedAccount savings(long accountId, long balance) {
        return linked(accountId, AccountType.SAVINGS, balance);
    }

    private static LinkedAccount demand(long accountId, long balance) {
        return linked(accountId, AccountType.DEMAND_DEPOSIT, balance);
    }

    private static LinkedAccount linked(long accountId, AccountType type, long balance) {
        return link(accountId, type, balance, balance, balance);
    }

    /** 다른 목표도 같은 계좌를 담은 상태(703). {@code allocatedTotal}이 이 목표 기여액보다 크면 몫이 1보다 작아진다. */
    private static LinkedAccount sharedDemand(long accountId, long balance, long contribution, long allocatedTotal) {
        return link(accountId, AccountType.DEMAND_DEPOSIT, balance, contribution, allocatedTotal);
    }

    private static LinkedAccount link(long accountId, AccountType type, long balance, long contribution, long allocatedTotal) {
        Account account = mock(Account.class);
        given(account.getId()).willReturn(accountId);
        given(account.getAccountType()).willReturn(type);
        given(account.getBalance()).willReturn(balance);
        return new LinkedAccount(null, account, contribution, new AccountAllocation(accountId, balance, allocatedTotal));
    }
}
