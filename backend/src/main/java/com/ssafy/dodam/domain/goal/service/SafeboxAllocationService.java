package com.ssafy.dodam.domain.goal.service;

import com.ssafy.dodam.domain.account.entity.Account;
import com.ssafy.dodam.domain.account.entity.AccountStatus;
import com.ssafy.dodam.domain.account.entity.AccountType;
import com.ssafy.dodam.domain.account.repository.AccountRepository;
import com.ssafy.dodam.domain.goal.dto.response.SafeboxAllocationResponse;
import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalAccount;
import com.ssafy.dodam.domain.goal.entity.GoalHistoryEvent;
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
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.repository.UserWriteLock;
import com.ssafy.dodam.global.repository.SafeboxOperationLock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 한 금융망 계좌 안의 목표별 금액 배정. 배정 자체에는 금융망 이체가 없다. */
@Service
public class SafeboxAllocationService {
    private final SafeboxAccountRepository safeboxAccounts;
    private final AccountRepository accounts;
    private final GoalRepository goals;
    private final GoalAccountRepository links;
    private final SafeboxMovementRepository movements;
    private final SavingGoalRepository savingGoals;
    private final GoalAllocationReader allocationReader;
    private final GoalHistoryRecorder history;
    private final GoalClock clock;
    private final UserWriteLock userLock;
    private final SafeboxOperationLock operationLock;
    private final TransactionTemplate transactions;

    public SafeboxAllocationService(SafeboxAccountRepository safeboxAccounts, AccountRepository accounts,
            GoalRepository goals, GoalAccountRepository links, SafeboxMovementRepository movements,
            SavingGoalRepository savingGoals, GoalAllocationReader allocationReader,
            GoalHistoryRecorder history, GoalClock clock, UserWriteLock userLock,
            SafeboxOperationLock operationLock, TransactionTemplate transactions) {
        this.safeboxAccounts = safeboxAccounts;
        this.accounts = accounts;
        this.goals = goals;
        this.links = links;
        this.movements = movements;
        this.savingGoals = savingGoals;
        this.allocationReader = allocationReader;
        this.history = history;
        this.clock = clock;
        this.userLock = userLock;
        this.operationLock = operationLock;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public SafeboxAllocationResponse get(Long userId, Long goalId) {
        Goal goal = ownedGoal(userId, goalId);
        if (goal.getType() != GoalType.SAVING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Account account = dedicatedAccount(userId);
        long allocated = links.findAllByGoalIdOrderByAccountIdAsc(goal.getId()).stream()
                .filter(link -> link.getAccountId().equals(account.getId()))
                .mapToLong(link -> link.getContributionAmount() == null ? account.getBalance() : link.getContributionAmount())
                .sum();
        return response(goalId, account, allocated, totalAllocated(userId, account.getId()));
    }

    public SafeboxAllocationResponse put(Long userId, Long goalId, long amount) {
        ReentrantLock lock = operationLock.forUser(userId);
        lock.lock();
        try {
            return transactions.execute(status -> putLocked(userId, goalId, amount));
        } finally {
            lock.unlock();
        }
    }

    private SafeboxAllocationResponse putLocked(Long userId, Long goalId, long amount) {
        if (amount < 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        userLock.lock(userId).orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        Goal goal = ownedGoal(userId, goalId);
        if (goal.getType() != GoalType.SAVING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Account account = dedicatedAccount(userId);
        List<GoalAccount> existing = links.findAllByGoalIdOrderByAccountIdAsc(goalId);
        if (existing.stream().anyMatch(item -> "DEMAND_DEPOSIT".equals(item.getAccountType())
                && !item.getAccountId().equals(account.getId()))) {
            throw new BusinessException(ErrorCode.SAFEBOX_LEGACY_LINK_CONFLICT);
        }
        GoalAccount link = existing.stream().filter(item -> item.getAccountId().equals(account.getId()))
                .findFirst().orElse(null);
        long before = link == null ? 0L : link.getContributionAmount() == null
                ? account.getBalance() : link.getContributionAmount();
        if (goal.getStatus().isTerminal() && amount > before) {
            throw new BusinessException(ErrorCode.GOAL_NOT_EDITABLE);
        }
        long total = totalAllocated(userId, account.getId());
        if (amount > before && (amount > account.getBalance()
                || amount > account.getBalance() - total + before)) {
            throw new BusinessException(ErrorCode.SAFEBOX_ALLOCATION_EXCEEDS_BALANCE);
        }
        if (amount != before) {
            LocalDateTime now = clock.now();
            if (link == null) {
                links.save(GoalAccount.allocate(userId, goalId, account.getId(), amount));
            } else {
                link.setContributionAmount(amount);
            }
            movements.save(new SafeboxMovement(userId, goalId, account.getId(), before, amount, now));
            goal.updateCurrentAmount(allocationReader.readGoal(userId, goalId).securedAmount(), now);
            history.record(goal, savingGoals.findById(goalId).orElse(null), GoalHistoryEvent.AMOUNT_UPDATED, now);
            total = total - before + amount;
        }
        return response(goalId, account, amount, total);
    }

    public long totalAllocated(Long userId, Long accountId) {
        Account account = accounts.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("계좌를 찾을 수 없습니다."));
        return links.findAllLiveByUserIdAndAccountIdIn(userId, List.of(accountId)).stream()
                .mapToLong(link -> link.getContributionAmount() == null ? account.getBalance() : link.getContributionAmount())
                .sum();
    }

    private SafeboxAllocationResponse response(Long goalId, Account account, long allocated, long total) {
        return new SafeboxAllocationResponse(goalId, account.getId(), allocated, account.getBalance(),
                Math.max(0L, account.getBalance() - total), Math.max(0L, total - account.getBalance()));
    }

    private Account dedicatedAccount(Long userId) {
        SafeboxAccount ref = safeboxAccounts.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SAFEBOX_ACCOUNT_NOT_FOUND));
        Account account = accounts.findByIdAndUserId(ref.getAccountId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SAFEBOX_ACCOUNT_NOT_FOUND));
        if (account.getAccountType() != AccountType.DEMAND_DEPOSIT || account.getStatus() == AccountStatus.CLOSED) {
            throw new BusinessException(ErrorCode.SAFEBOX_ACCOUNT_NOT_FOUND);
        }
        return account;
    }

    private Goal ownedGoal(Long userId, Long goalId) {
        return goals.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("목표를 찾을 수 없습니다."));
    }
}
