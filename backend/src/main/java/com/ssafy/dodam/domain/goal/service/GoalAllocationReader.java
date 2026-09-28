package com.ssafy.dodam.domain.goal.service;

import com.ssafy.dodam.domain.account.entity.Account;
import com.ssafy.dodam.domain.account.entity.AccountType;
import com.ssafy.dodam.domain.account.repository.AccountRepository;
import com.ssafy.dodam.domain.goal.entity.GoalAccount;
import com.ssafy.dodam.domain.goal.repository.GoalAccountRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 모으기 목표 확보액과 계좌별 할당 현황을 한 곳에서 계산한다(S15P21D105-626).
 *
 * <p>목표 API(510·509)·진단·저축 시뮬레이션이 각자 "연결 계좌 잔액 합"을 구하던 것을 이 클래스로 모아,
 * 입출금 계좌를 여러 목표에 나눠 담기 시작한 뒤에도 같은 돈이 두 목표에 중복 합산되지 않게 한다.
 *
 * <pre>
 * 링크 기여액        = contribution_amount가 있으면 그 값, 없으면 계좌 잔액 전체(0 미만은 0)
 *                      · 예·적금은 항상 NULL(전액), 입출금 NULL은 626 이전 방식(전액)
 * 목표 확보액        = 그 목표의 링크 기여액 합
 * 계좌 allocatedTotal = 그 계좌가 삭제되지 않은 모든 목표에 기여하는 금액 합(목표 상태·페이지와 무관)
 * availableToAllocate = max(0, 잔액 − allocatedTotal)
 * shortfallAmount     = max(0, allocatedTotal − max(0, 잔액))   — 실제 출금으로 설정액이 잔액을 넘은 상태
 * </pre>
 *
 * <p>트랜잭션은 호출한 서비스의 것을 쓴다. 조회만 하며 `goal.current_amount` 갱신은 호출자가 한다.
 */
@Component
public class GoalAllocationReader {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_NEEDS_ADJUSTMENT = "NEEDS_ADJUSTMENT";

    private final GoalAccountRepository goalAccountRepository;
    private final AccountRepository accountRepository;

    public GoalAllocationReader(GoalAccountRepository goalAccountRepository, AccountRepository accountRepository) {
        this.goalAccountRepository = goalAccountRepository;
        this.accountRepository = accountRepository;
    }

    /** 예·적금은 계좌 하나가 목표 하나에만 전액 연결된다. 입출금만 여러 목표에 금액을 나눠 담을 수 있다. */
    public static boolean isExclusiveType(AccountType type) {
        return type == AccountType.DEPOSIT || type == AccountType.SAVINGS;
    }

    public static long contributionOf(GoalAccount link, Account account) {
        long balance = Math.max(0L, account.getBalance());
        return link.getContributionAmount() == null ? balance : link.getContributionAmount();
    }

    /** 목표 하나의 링크·계좌·확보액과, 연결된 각 계좌의 사용자 전체 기준 할당 현황. 링크는 계좌 ID 오름차순. */
    public GoalAllocation readGoal(Long userId, Long goalId) {
        List<GoalAccount> links = goalAccountRepository.findAllByGoalIdOrderByAccountIdAsc(goalId);
        Map<Long, Account> accounts = accountsOf(links);
        Map<Long, AccountAllocation> accountAllocations = readAccounts(userId, accounts.values());
        return toGoalAllocation(goalId, links, accounts, accountAllocations);
    }

    /** 여러 목표의 확보액을 한 번에 읽는다(시뮬레이션 기준선·목록용). 계좌별 할당 현황은 함께 계산한다. */
    public Map<Long, GoalAllocation> readGoals(Long userId, Collection<Long> goalIds) {
        if (goalIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<GoalAccount>> linksByGoal = goalAccountRepository.findAllByUserIdAndGoalIdIn(userId, goalIds).stream()
                .sorted((a, b) -> Long.compare(a.getAccountId(), b.getAccountId()))
                .collect(Collectors.groupingBy(GoalAccount::getGoalId, LinkedHashMap::new, Collectors.toList()));
        List<GoalAccount> all = linksByGoal.values().stream().flatMap(List::stream).toList();
        Map<Long, Account> accounts = accountsOf(all);
        Map<Long, AccountAllocation> accountAllocations = readAccounts(userId, accounts.values());

        Map<Long, GoalAllocation> result = new LinkedHashMap<>();
        for (Long goalId : goalIds) {
            result.put(goalId, toGoalAllocation(goalId, linksByGoal.getOrDefault(goalId, List.of()), accounts, accountAllocations));
        }
        return result;
    }

    /**
     * 계좌별 할당 현황. 삭제되지 않은 목표의 링크만 합산하므로, 삭제된 목표에 남은 링크(509의 stale link)는 가용액을 줄이지 않는다.
     */
    public Map<Long, AccountAllocation> readAccounts(Long userId, Collection<Account> accounts) {
        if (accounts.isEmpty()) {
            return Map.of();
        }
        Map<Long, Account> byId = accounts.stream().collect(Collectors.toMap(Account::getId, Function.identity(), (a, b) -> a));
        Map<Long, Long> allocatedTotals = goalAccountRepository.findAllLiveByUserIdAndAccountIdIn(userId, byId.keySet()).stream()
                .collect(Collectors.groupingBy(GoalAccount::getAccountId,
                        Collectors.summingLong(link -> contributionOf(link, byId.get(link.getAccountId())))));
        Map<Long, AccountAllocation> result = new LinkedHashMap<>();
        for (Account account : byId.values()) {
            result.put(account.getId(), new AccountAllocation(
                    account.getId(), account.getBalance(), allocatedTotals.getOrDefault(account.getId(), 0L)));
        }
        return result;
    }

    private Map<Long, Account> accountsOf(List<GoalAccount> links) {
        if (links.isEmpty()) {
            return Map.of();
        }
        return accountRepository.findAllById(links.stream().map(GoalAccount::getAccountId).distinct().toList()).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
    }

    private static GoalAllocation toGoalAllocation(
            Long goalId, List<GoalAccount> links, Map<Long, Account> accounts, Map<Long, AccountAllocation> accountAllocations) {
        // FK로 계좌는 항상 존재하지만, 방어적으로 계좌 행이 없는 링크는 확보액과 목록에서 제외한다(509와 같은 처리)
        List<LinkedAccount> items = links.stream()
                .filter(link -> accounts.containsKey(link.getAccountId()))
                .map(link -> {
                    Account account = accounts.get(link.getAccountId());
                    return new LinkedAccount(link, account, contributionOf(link, account), accountAllocations.get(account.getId()));
                })
                .toList();
        long secured = items.stream().mapToLong(LinkedAccount::contribution).sum();
        return new GoalAllocation(goalId, items, secured);
    }

    /**
     * @param securedAmount 목표 확보액 = 링크 기여액 합. `goal.current_amount`에 저장되는 값
     */
    public record GoalAllocation(Long goalId, List<LinkedAccount> accounts, long securedAmount) {

        public GoalAllocation {
            accounts = List.copyOf(accounts);
        }

        public List<Long> accountIds() {
            return accounts.stream().map(item -> item.account().getId()).toList();
        }
    }

    /**
     * @param contribution 이 링크가 목표에 기여하는 금액(allocatedToThisGoal)
     * @param allocation   이 계좌의 사용자 전체 기준 할당 현황
     */
    public record LinkedAccount(GoalAccount link, Account account, long contribution, AccountAllocation allocation) {

        public long allocatedElsewhere() {
            return Math.max(0L, allocation.allocatedTotal() - contribution);
        }
    }

    /**
     * @param balance        마지막 동기화 잔액(원문)
     * @param allocatedTotal 삭제되지 않은 모든 목표에 담긴 금액 합. 예·적금은 잔액 전체
     */
    public record AccountAllocation(Long accountId, long balance, long allocatedTotal) {

        public long availableToAllocate() {
            return Math.max(0L, balance - allocatedTotal);
        }

        public long shortfallAmount() {
            return Math.max(0L, allocatedTotal - Math.max(0L, balance));
        }

        public String status() {
            return shortfallAmount() > 0 ? STATUS_NEEDS_ADJUSTMENT : STATUS_OK;
        }
    }
}
