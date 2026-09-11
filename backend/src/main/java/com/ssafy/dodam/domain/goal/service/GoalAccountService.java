package com.ssafy.dodam.domain.goal.service;

import com.ssafy.dodam.domain.account.entity.Account;
import com.ssafy.dodam.domain.account.entity.AccountStatus;
import com.ssafy.dodam.domain.account.entity.AccountType;
import com.ssafy.dodam.domain.account.repository.AccountRepository;
import com.ssafy.dodam.domain.goal.dto.response.GoalAccountsResponse;
import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalAccount;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.repository.GoalAccountRepository;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 12 목표 연결계좌(S15P21D105-509).
 *
 * <p>모으기(SAVING) 목표에만 계좌를 연결할 수 있고(ERD `fk_goal_account_saving_goal`), 한 계좌는 한 목표에만 연결된다
 * (`uk_goal_account_account`). 연결이 바뀌면 목표의 모인 금액(`goal.current_amount`)을 연결 계좌 잔액 합으로 다시 놓는다.
 * 계좌 잔액이 동기화로 바뀐 뒤의 재계산은 510(진행률)에서 다룬다(remaining-work).
 */
@Service
@Transactional
public class GoalAccountService {

    static final String ACCOUNT_NOT_FOUND_MESSAGE = "계좌를 찾을 수 없습니다.";
    static final String SPENDING_GOAL_MESSAGE = "줄이기 목표에는 계좌를 연결할 수 없습니다.";
    static final String ACCOUNT_TYPE_MESSAGE = "입출금·예금·적금 계좌만 목표에 연결할 수 있습니다.";
    static final String ACCOUNT_CLOSED_MESSAGE = "해지된 계좌는 목표에 연결할 수 없습니다.";

    /** ERD `chk_goal_account_type`과 같은 값. 마이너스통장은 부채라 연결할 수 없다. */
    private static final Set<AccountType> LINKABLE_TYPES = EnumSet.of(
            AccountType.DEMAND_DEPOSIT, AccountType.DEPOSIT, AccountType.SAVINGS);

    private final GoalRepository goalRepository;
    private final GoalAccountRepository goalAccountRepository;
    private final AccountRepository accountRepository;
    private final SensitiveNumberHasher sensitiveNumberHasher;
    private final GoalClock goalClock;

    public GoalAccountService(
            GoalRepository goalRepository,
            GoalAccountRepository goalAccountRepository,
            AccountRepository accountRepository,
            SensitiveNumberHasher sensitiveNumberHasher,
            GoalClock goalClock
    ) {
        this.goalRepository = goalRepository;
        this.goalAccountRepository = goalAccountRepository;
        this.accountRepository = accountRepository;
        this.sensitiveNumberHasher = sensitiveNumberHasher;
        this.goalClock = goalClock;
    }

    /**
     * 줄이기 목표는 연결할 수 없으므로 항상 빈 목록이다(404가 아니다).
     */
    @Transactional(readOnly = true)
    public GoalAccountsResponse getAccounts(Long userId, Long goalId, int page, int size) {
        Goal goal = findOwned(userId, goalId);
        List<GoalAccount> links = goalAccountRepository.findAllByGoalIdOrderByAccountIdAsc(goal.getId());
        return toResponse(goal, links, page, size);
    }

    /**
     * 연결 계좌를 요청 목록으로 통째로 바꾼다. 빈 배열은 모두 해제다.
     * 검증(소유권·유형·상태·다른 목표 연결)을 모두 끝낸 뒤 반영하므로 부분 반영은 없다.
     */
    public GoalAccountsResponse replaceAccounts(Long userId, Long goalId, List<String> accountNos) {
        Goal goal = findOwned(userId, goalId);
        if (goal.getType() != GoalType.SAVING) {
            throw new InvalidRequestException(SPENDING_GOAL_MESSAGE);
        }
        if (goal.getStatus().isTerminal()) {
            throw new BusinessException(ErrorCode.GOAL_NOT_EDITABLE);
        }

        // 요청 계좌 확인: 순서 유지, 중복 제거
        Map<Long, Account> requested = new LinkedHashMap<>();
        for (String accountNo : new LinkedHashSet<>(accountNos)) {
            Account account = accountRepository
                    .findByUserIdAndAccountNoHash(userId, sensitiveNumberHasher.hash(accountNo))
                    .orElseThrow(() -> new ResourceNotFoundException(ACCOUNT_NOT_FOUND_MESSAGE));
            if (!LINKABLE_TYPES.contains(account.getAccountType())) {
                throw new InvalidRequestException(ACCOUNT_TYPE_MESSAGE);
            }
            if (account.getStatus() == AccountStatus.CLOSED) {
                throw new InvalidRequestException(ACCOUNT_CLOSED_MESSAGE);
            }
            requested.put(account.getId(), account);
        }

        // 다른(삭제되지 않은) 목표에 이미 연결된 계좌는 거절한다. 삭제된 목표의 연결은 해제하고 이어받는다.
        List<GoalAccount> existing = goalAccountRepository.findAllByGoalIdOrderByAccountIdAsc(goal.getId());
        Set<Long> existingIds = existing.stream().map(GoalAccount::getAccountId).collect(Collectors.toSet());
        List<GoalAccount> linkedElsewhere = requested.isEmpty()
                ? List.of()
                : goalAccountRepository.findAllByAccountIdIn(requested.keySet()).stream()
                        .filter(link -> !link.getGoalId().equals(goal.getId()))
                        .toList();
        List<GoalAccount> staleLinks = new ArrayList<>();
        for (GoalAccount link : linkedElsewhere) {
            boolean ownerAlive = goalRepository.findByIdAndUserIdAndDeletedAtIsNull(link.getGoalId(), userId).isPresent();
            if (ownerAlive) {
                throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_LINKED_TO_GOAL);
            }
            staleLinks.add(link);
        }

        LocalDateTime now = goalClock.now();
        if (!existingIds.equals(requested.keySet())) {
            goalAccountRepository.deleteAll(staleLinks);
            goalAccountRepository.deleteAll(existing.stream()
                    .filter(link -> !requested.containsKey(link.getAccountId()))
                    .toList());
            goalAccountRepository.flush();
            List<GoalAccount> added = requested.values().stream()
                    .filter(account -> !existingIds.contains(account.getId()))
                    .map(account -> GoalAccount.link(userId, goal.getId(), account.getId(), account.getAccountType().name()))
                    .toList();
            goalAccountRepository.saveAll(added);
        }

        long total = requested.values().stream().mapToLong(Account::getBalance).sum();
        goal.updateCurrentAmount(total, now);

        List<GoalAccount> links = goalAccountRepository.findAllByGoalIdOrderByAccountIdAsc(goal.getId());
        return toResponse(goal, links, 0, Math.max(1, Math.max(links.size(), 20)));
    }

    // ---------- helpers ----------

    private Goal findOwned(Long userId, Long goalId) {
        return goalRepository.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(GoalService.GOAL_NOT_FOUND_MESSAGE));
    }

    private GoalAccountsResponse toResponse(Goal goal, List<GoalAccount> links, int page, int size) {
        Map<Long, Account> accounts = links.isEmpty()
                ? Map.of()
                : accountRepository.findAllById(links.stream().map(GoalAccount::getAccountId).toList()).stream()
                        .collect(Collectors.toMap(Account::getId, Function.identity()));
        long totalBalance = links.stream()
                .map(link -> accounts.get(link.getAccountId()))
                .filter(account -> account != null)
                .mapToLong(Account::getBalance)
                .sum();

        int from = Math.min(page * size, links.size());
        int to = Math.min(from + size, links.size());
        List<GoalAccountsResponse.Item> items = links.subList(from, to).stream()
                .filter(link -> accounts.containsKey(link.getAccountId()))
                .map(link -> GoalAccountsResponse.Item.from(link, accounts.get(link.getAccountId())))
                .toList();
        int totalPages = links.isEmpty() ? 0 : (int) Math.ceil(links.size() / (double) size);
        return new GoalAccountsResponse(
                goal.getId(), links.size(), totalBalance, items, page, size, totalPages, to >= links.size());
    }
}
