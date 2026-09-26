package com.ssafy.dodam.domain.goal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.dodam.domain.account.entity.Account;
import com.ssafy.dodam.domain.account.entity.AccountStatus;
import com.ssafy.dodam.domain.account.entity.AccountType;
import com.ssafy.dodam.domain.account.repository.AccountRepository;
import com.ssafy.dodam.domain.goal.dto.response.SafeboxAllocationResponse;
import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalAccount;
import com.ssafy.dodam.domain.goal.entity.GoalStatus;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.entity.SafeboxAccount;
import com.ssafy.dodam.domain.goal.entity.SafeboxMovement;
import com.ssafy.dodam.domain.goal.repository.GoalAccountRepository;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.goal.repository.SafeboxAccountRepository;
import com.ssafy.dodam.domain.goal.repository.SafeboxMovementRepository;
import com.ssafy.dodam.domain.goal.repository.SavingGoalRepository;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.repository.SafeboxOperationLock;
import com.ssafy.dodam.global.repository.UserWriteLock;
import com.ssafy.dodam.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class SafeboxAllocationServiceTest {
    private static final Long USER_ID = 1L;
    private static final Long GOAL_ID = 10L;
    private static final Long ACCOUNT_ID = 20L;

    private final SafeboxAccountRepository safeboxAccounts = mock(SafeboxAccountRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final GoalRepository goals = mock(GoalRepository.class);
    private final GoalAccountRepository links = mock(GoalAccountRepository.class);
    private final SafeboxMovementRepository movements = mock(SafeboxMovementRepository.class);
    private final SavingGoalRepository savingGoals = mock(SavingGoalRepository.class);
    private final GoalAllocationReader allocationReader = mock(GoalAllocationReader.class);
    private final GoalHistoryRecorder history = mock(GoalHistoryRecorder.class);
    private final GoalClock clock = mock(GoalClock.class);
    private final UserWriteLock userLock = mock(UserWriteLock.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final SafeboxAllocationService service = new SafeboxAllocationService(safeboxAccounts,
            accounts, goals, links, movements, savingGoals, allocationReader, history, clock,
            userLock, new SafeboxOperationLock(), transactions);

    private final Goal goal = mock(Goal.class);
    private final Account account = mock(Account.class);

    @BeforeEach
    void setUp() {
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        when(userLock.lock(USER_ID)).thenReturn(Optional.of(mock(User.class)));
        when(goals.findByIdAndUserIdAndDeletedAtIsNull(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));
        when(goal.getType()).thenReturn(GoalType.SAVING);
        when(goal.getStatus()).thenReturn(GoalStatus.ACTIVE);
        when(goal.getId()).thenReturn(GOAL_ID);
        when(safeboxAccounts.findById(USER_ID)).thenReturn(Optional.of(
                new SafeboxAccount(USER_ID, ACCOUNT_ID, LocalDateTime.of(2026, 9, 1, 0, 0))));
        when(accounts.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(account));
        when(account.getId()).thenReturn(ACCOUNT_ID);
        when(account.getAccountType()).thenReturn(AccountType.DEMAND_DEPOSIT);
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(account.getBalance()).thenReturn(1_000_000L);
        when(links.findAllByGoalIdOrderByAccountIdAsc(GOAL_ID)).thenReturn(List.of());
        when(clock.now()).thenReturn(LocalDateTime.of(2026, 9, 26, 10, 0));
    }

    @Test
    void allocatesOnlyUnassignedBalanceAndUpdatesGoal() {
        when(links.findAllLiveByUserIdAndAccountIdIn(eq(USER_ID), any())).thenReturn(List.of(
                GoalAccount.allocate(USER_ID, 11L, ACCOUNT_ID, 400_000L)));
        when(allocationReader.readGoal(USER_ID, GOAL_ID)).thenReturn(
                new GoalAllocationReader.GoalAllocation(GOAL_ID, List.of(), 500_000L));

        SafeboxAllocationResponse result = service.put(USER_ID, GOAL_ID, 500_000L);

        assertThat(result.allocatedAmount()).isEqualTo(500_000L);
        assertThat(result.availableAmount()).isEqualTo(100_000L);
        verify(links).save(any(GoalAccount.class));
        verify(movements).save(any(SafeboxMovement.class));
        verify(goal).updateCurrentAmount(eq(500_000L), any(LocalDateTime.class));
    }

    @Test
    void refusesToUseMoneyAlreadyAssignedToAnotherGoal() {
        when(links.findAllLiveByUserIdAndAccountIdIn(eq(USER_ID), any())).thenReturn(List.of(
                GoalAccount.allocate(USER_ID, 11L, ACCOUNT_ID, 800_000L)));

        assertThatThrownBy(() -> service.put(USER_ID, GOAL_ID, 300_000L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SAFEBOX_ALLOCATION_EXCEEDS_BALANCE));
    }
}
