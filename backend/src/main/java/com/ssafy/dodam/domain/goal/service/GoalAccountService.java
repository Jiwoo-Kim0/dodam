package com.ssafy.dodam.domain.goal.service;

import com.ssafy.dodam.domain.account.entity.Account;
import com.ssafy.dodam.domain.account.entity.AccountStatus;
import com.ssafy.dodam.domain.account.entity.AccountType;
import com.ssafy.dodam.domain.account.repository.AccountRepository;
import com.ssafy.dodam.domain.goal.dto.response.GoalAccountsResponse;
import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalAccount;
import com.ssafy.dodam.domain.goal.entity.GoalHistoryEvent;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.repository.SavingGoalRepository;
import com.ssafy.dodam.domain.goal.repository.GoalAllocationUserLock;
import com.ssafy.dodam.domain.goal.repository.GoalAccountRepository;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 12 목표 연결계좌(S15P21D105-509).
 *
 * <p>모으기(SAVING) 목표에만 계좌를 연결할 수 있고(ERD `fk_goal_account_saving_goal`), 한 계좌는 한 목표에만 연결된다
 * (`uk_goal_account_account`; 626 V35부터 DB 제약은 예·적금에만 남고 입출금은 할당 API가 따로 다룬다). 연결이 바뀌면
 * 목표의 모인 금액(`goal.current_amount`)을 {@link GoalAllocationReader}의 확보액으로 다시 놓고 이력(511)을 남긴다.
 * 계좌 잔액이 동기화로 바뀐 뒤의 재계산은 510 계산 API가 조회 시점에 한다.
 */
@Service
@Transactional
public class GoalAccountService {

    static final String ACCOUNT_NOT_FOUND_MESSAGE = "계좌를 찾을 수 없습니다.";
    static final String SPENDING_GOAL_MESSAGE = "줄이기 목표에는 계좌를 연결할 수 없습니다.";
    static final String ACCOUNT_TYPE_MESSAGE = "예금·적금 계좌만 연결할 수 있습니다. 입출금 계좌는 담기·꺼내기를 이용해 주세요.";
    static final String ACCOUNT_CLOSED_MESSAGE = "해지된 계좌는 목표에 연결할 수 없습니다.";
    static final String ALLOCATION_TYPE_MESSAGE = "자유입출금 계좌만 목표에 담을 수 있습니다.";

    /** ERD `chk_goal_account_type`과 같은 값. 마이너스통장은 부채라 연결할 수 없다. */
    private static final Set<AccountType> DIRECT_LINK_TYPES = EnumSet.of(AccountType.DEPOSIT, AccountType.SAVINGS);

    private final GoalRepository goalRepository;
    private final GoalAllocationUserLock goalAllocationUserLock;
    private final GoalAccountRepository goalAccountRepository;
    private final SavingGoalRepository savingGoalRepository;
    private final AccountRepository accountRepository;
    private final GoalHistoryRecorder goalHistoryRecorder;
    private final GoalClock goalClock;
    private final GoalAllocationReader allocationReader;

    public GoalAccountService(
            GoalRepository goalRepository,
            GoalAllocationUserLock goalAllocationUserLock,
            GoalAccountRepository goalAccountRepository,
            SavingGoalRepository savingGoalRepository,
            AccountRepository accountRepository,
            GoalHistoryRecorder goalHistoryRecorder,
            GoalClock goalClock,
            GoalAllocationReader allocationReader
    ) {
        this.goalRepository = goalRepository;
        this.goalAllocationUserLock = goalAllocationUserLock;
        this.goalAccountRepository = goalAccountRepository;
        this.savingGoalRepository = savingGoalRepository;
        this.accountRepository = accountRepository;
        this.goalHistoryRecorder = goalHistoryRecorder;
        this.goalClock = goalClock;
        this.allocationReader = allocationReader;
    }

    /**
     * 줄이기 목표는 연결할 수 없으므로 항상 빈 목록이다(404가 아니다).
     */
    @Transactional(readOnly = true)
    public GoalAccountsResponse getAccounts(Long userId, Long goalId, int page, int size) {
        Goal goal = findOwned(userId, goalId);
        return toResponse(goal, allocationReader.readGoal(userId, goal.getId()), page, size);
    }

    /**
     * 예·적금 연결을 요청 목록으로 통째로 바꾼다. 빈 배열은 예·적금만 해제하며 입출금 할당은 유지한다.
     * 검증(소유권·유형·상태·다른 목표 연결)을 모두 끝낸 뒤 반영하므로 부분 반영은 없다.
     *
     * @param accountIds 계좌 응답의 `accountId` 목록. 타인 계좌·없는 계좌는 구분하지 않고 404다(존재 여부를 드러내지 않기 위해)
     */
    public GoalAccountsResponse replaceAccounts(Long userId, Long goalId, List<Long> accountIds) {
        lockUser(userId);
        Goal goal = findOwned(userId, goalId);
        if (goal.getType() != GoalType.SAVING) {
            throw new InvalidRequestException(SPENDING_GOAL_MESSAGE);
        }
        if (goal.getStatus().isTerminal()) {
            throw new BusinessException(ErrorCode.GOAL_NOT_EDITABLE);
        }

        // 요청 계좌 확인: 순서 유지, 중복 제거
        Map<Long, Account> requested = new LinkedHashMap<>();
        for (Long accountId : new LinkedHashSet<>(accountIds)) {
            Account account = accountRepository
                    .findByIdAndUserId(accountId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException(ACCOUNT_NOT_FOUND_MESSAGE));
            if (!DIRECT_LINK_TYPES.contains(account.getAccountType())) {
                throw new InvalidRequestException(ACCOUNT_TYPE_MESSAGE);
            }
            if (account.getStatus() == AccountStatus.CLOSED) {
                throw new InvalidRequestException(ACCOUNT_CLOSED_MESSAGE);
            }
            requested.put(account.getId(), account);
        }

        // 다른(삭제되지 않은) 목표에 이미 연결된 계좌는 거절한다. 삭제된 목표의 연결은 해제하고 이어받는다.
        List<GoalAccount> existing = goalAccountRepository.findAllByGoalIdOrderByAccountIdAsc(goal.getId());
        List<GoalAccount> existingDirectLinks = existing.stream()
                .filter(link -> DIRECT_LINK_TYPES.contains(AccountType.valueOf(link.getAccountType())))
                .toList();
        Set<Long> existingDirectIds = existingDirectLinks.stream()
                .map(GoalAccount::getAccountId)
                .collect(Collectors.toSet());
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
        boolean setChanged = !existingDirectIds.equals(requested.keySet());
        if (setChanged) {
            goalAccountRepository.deleteAll(staleLinks);
            goalAccountRepository.deleteAll(existingDirectLinks.stream()
                    .filter(link -> !requested.containsKey(link.getAccountId()))
                    .toList());
            goalAccountRepository.flush();
            List<GoalAccount> added = requested.values().stream()
                    .filter(account -> !existingDirectIds.contains(account.getId()))
                    .map(account -> GoalAccount.link(userId, goal.getId(), account.getId(), account.getAccountType().name()))
                    .toList();
            goalAccountRepository.saveAll(added);
        }

        // 새 링크는 contribution_amount가 null(전액)이고 기존 입출금 할당액은 그대로 두므로, 확보액은 Reader 정의로 다시 읽는다
        GoalAllocationReader.GoalAllocation allocation = allocationReader.readGoal(userId, goal.getId());
        boolean amountChanged = goal.updateCurrentAmount(allocation.securedAmount(), now);
        if (setChanged || amountChanged) {
            goalHistoryRecorder.record(goal, savingGoalRepository.findById(goal.getId()).orElse(null),
                    setChanged ? GoalHistoryEvent.ACCOUNTS_UPDATED : GoalHistoryEvent.AMOUNT_UPDATED, now);
        }

        int linked = allocation.accounts().size();
        return toResponse(goal, allocation, 0, Math.max(1, Math.max(linked, 20)));
    }

    /**
     * 자유입출금 담기·꺼내기(703). 절대값 하나로 담기·꺼내기를 모두 처리한다 — 더 작은 값이면 꺼내기, 0이면 할당 해제다.
     * 같은 요청을 여러 번 보내도 결과가 같다(멱등).
     *
     * <p>검증은 {@code amount ≤ 잔액 − 다른 목표에 담긴 금액} 하나다. 그 계좌를 담은 다른 목표가 삭제됐으면 그 할당은
     * 세지 않는다(확보액 정의와 같다). 실제 출금으로 잔액이 줄어 부족 상태가 된 계좌는 담을 수 있는 금액이 0이라 증가가 막히고
     * 꺼내기는 허용된다 — 사용자가 스스로 조정하게 두는 626 §7 정책이다.
     *
     * <p>종료된 목표(ACHIEVED·CANCELED)에서는 <b>늘리기만</b> 막는다. 이미 담아 둔 돈을 빼지 못하면 사용자가 자금을 되찾을
     * 길이 없기 때문이다(626 §2 확정).
     *
     * <p>예·적금은 대상이 아니다. 계좌 하나가 목표 하나에 전액 연결되므로 나눠 담을 금액이라는 개념이 없다.
     *
     * @param amount 담아 둘 금액(원). 0이면 연결을 해제한다
     */
    public GoalAccountsResponse updateAllocation(Long userId, Long goalId, Long accountId, long amount) {
        lockUser(userId);
        Goal goal = findOwned(userId, goalId);
        if (goal.getType() != GoalType.SAVING) {
            throw new InvalidRequestException(SPENDING_GOAL_MESSAGE);
        }
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ACCOUNT_NOT_FOUND_MESSAGE));
        if (account.getAccountType() != AccountType.DEMAND_DEPOSIT) {
            throw new InvalidRequestException(ALLOCATION_TYPE_MESSAGE);
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new InvalidRequestException(ACCOUNT_CLOSED_MESSAGE);
        }

        List<GoalAccount> links = goalAccountRepository.findAllByAccountIdIn(List.of(accountId));
        GoalAccount mine = links.stream().filter(link -> link.getGoalId().equals(goal.getId())).findFirst().orElse(null);
        long current = mine == null ? 0L : GoalAllocationReader.contributionOf(mine, account);
        if (amount > current && goal.getStatus().isTerminal()) {
            throw new BusinessException(ErrorCode.GOAL_NOT_EDITABLE);
        }
        long allocatedElsewhere = links.stream()
                .filter(link -> !link.getGoalId().equals(goal.getId()))
                .filter(link -> goalRepository.findByIdAndUserIdAndDeletedAtIsNull(link.getGoalId(), userId).isPresent())
                .mapToLong(link -> GoalAllocationReader.contributionOf(link, account))
                .sum();
        if (amount > Math.max(0L, account.getBalance()) - allocatedElsewhere) {
            throw new BusinessException(ErrorCode.GOAL_ALLOCATION_EXCEEDS_AVAILABLE);
        }

        boolean linkChanged = false;
        if (amount == 0L) {
            if (mine != null) {
                goalAccountRepository.delete(mine);
                goalAccountRepository.flush();
                linkChanged = true;
            }
        } else if (mine == null) {
            GoalAccount added = GoalAccount.link(userId, goal.getId(), accountId, account.getAccountType().name());
            added.allocate(amount);
            goalAccountRepository.save(added);
            linkChanged = true;
        } else {
            mine.allocate(amount);
            linkChanged = current != amount;
        }

        LocalDateTime now = goalClock.now();
        GoalAllocationReader.GoalAllocation allocation = allocationReader.readGoal(userId, goal.getId());
        boolean amountChanged = goal.updateCurrentAmount(allocation.securedAmount(), now);
        if (linkChanged || amountChanged) {
            goalHistoryRecorder.record(goal, savingGoalRepository.findById(goal.getId()).orElse(null),
                    GoalHistoryEvent.ACCOUNTS_UPDATED, now);
        }
        int linked = allocation.accounts().size();
        return toResponse(goal, allocation, 0, Math.max(1, Math.max(linked, 20)));
    }

    // ---------- helpers ----------

    private void lockUser(Long userId) {
        goalAllocationUserLock.lock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
    }

    private Goal findOwned(Long userId, Long goalId) {
        return goalRepository.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(GoalService.GOAL_NOT_FOUND_MESSAGE));
    }

    private GoalAccountsResponse toResponse(Goal goal, GoalAllocationReader.GoalAllocation allocation, int page, int size) {
        List<GoalAllocationReader.LinkedAccount> links = allocation.accounts();
        int from = Math.min(page * size, links.size());
        int to = Math.min(from + size, links.size());
        List<GoalAccountsResponse.Item> items = links.subList(from, to).stream()
                .map(GoalAccountsResponse.Item::from)
                .toList();
        int totalPages = links.isEmpty() ? 0 : (int) Math.ceil(links.size() / (double) size);
        return new GoalAccountsResponse(
                goal.getId(), links.size(), allocation.securedAmount(), items, page, size, totalPages, to >= links.size());
    }
}
