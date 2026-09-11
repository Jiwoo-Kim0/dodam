package com.ssafy.dodam.domain.account.service;

import com.ssafy.dodam.domain.account.dto.response.AccountBalanceSummaryResponse;
import com.ssafy.dodam.domain.account.dto.response.AccountMonthlySummaryResponse;
import com.ssafy.dodam.domain.account.dto.response.AccountSummaryResponse;
import com.ssafy.dodam.domain.account.dto.response.AccountTrendResponse;
import com.ssafy.dodam.domain.account.dto.response.InactiveAccountsResponse;
import com.ssafy.dodam.domain.account.entity.Account;
import com.ssafy.dodam.domain.account.entity.AccountStatus;
import com.ssafy.dodam.domain.account.entity.AccountType;
import com.ssafy.dodam.domain.account.repository.AccountMonthlyFlowRow;
import com.ssafy.dodam.domain.account.repository.AccountRepository;
import com.ssafy.dodam.domain.account.repository.JdbcAccountFlowReader;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.policy.YearMonthPeriod;
import com.ssafy.dodam.global.policy.YearMonthPeriodPolicy;
import com.ssafy.dodam.global.policy.YearMonthPolicy;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 5 계좌 집계·휴면 탐지(S15P21D105-503). 모두 우리 DB(`account`, `transaction`) 기준 자체 계산이다.
 *
 * <p>계좌 단위 집계(요약·현금성 자산·휴면)는 사용자 계좌를 한 번 읽어 메모리에서 계산한다. 사용자당 계좌는 수십 건을 넘지 않는다.
 * 거래 단위 집계(월별 요약·추이)는 {@link JdbcAccountFlowReader}가 월별로 묶어 읽는다.
 */
@Service
@Transactional(readOnly = true)
public class AccountAnalysisService {

    public static final int DEFAULT_INACTIVE_MONTHS = 6;

    private final AccountRepository accountRepository;
    private final JdbcAccountFlowReader accountFlowReader;
    private final SensitiveNumberHasher sensitiveNumberHasher;
    private final YearMonthPolicy yearMonthPolicy;
    private final YearMonthPeriodPolicy yearMonthPeriodPolicy;
    private final AccountClock accountClock;

    public AccountAnalysisService(
            AccountRepository accountRepository,
            JdbcAccountFlowReader accountFlowReader,
            SensitiveNumberHasher sensitiveNumberHasher,
            YearMonthPolicy yearMonthPolicy,
            YearMonthPeriodPolicy yearMonthPeriodPolicy,
            AccountClock accountClock
    ) {
        this.accountRepository = accountRepository;
        this.accountFlowReader = accountFlowReader;
        this.sensitiveNumberHasher = sensitiveNumberHasher;
        this.yearMonthPolicy = yearMonthPolicy;
        this.yearMonthPeriodPolicy = yearMonthPeriodPolicy;
        this.accountClock = accountClock;
    }

    // ---------- 계좌 요약 ----------

    /**
     * Finance Data 통합 요약(500)의 계좌 절과 같은 규칙: CLOSED는 개수·잔액에서 빼고, 마이너스통장 잔액(0 이하)은 사용액으로 뒤집는다.
     */
    public AccountSummaryResponse getSummary(Long userId) {
        List<Account> accounts = accountRepository.findAllByUserId(userId);
        List<Account> open = accounts.stream().filter(account -> account.getStatus() != AccountStatus.CLOSED).toList();

        long demandDepositBalance = balanceOf(open, AccountType.DEMAND_DEPOSIT);
        long depositBalance = balanceOf(open, AccountType.DEPOSIT);
        long savingsBalance = balanceOf(open, AccountType.SAVINGS);
        long minusUsedAmount = -balanceOf(open, AccountType.MINUS);
        long totalAssets = demandDepositBalance + depositBalance + savingsBalance;

        return new AccountSummaryResponse(
                open.size(),
                countByStatus(accounts, AccountStatus.ACTIVE),
                countByStatus(accounts, AccountStatus.INACTIVE),
                countByStatus(accounts, AccountStatus.CLOSED),
                countOf(open, AccountType.DEMAND_DEPOSIT),
                demandDepositBalance,
                countOf(open, AccountType.DEPOSIT),
                depositBalance,
                countOf(open, AccountType.SAVINGS),
                savingsBalance,
                countOf(open, AccountType.MINUS),
                minusUsedAmount,
                totalAssets,
                totalAssets - minusUsedAmount
        );
    }

    // ---------- 현금성 자산 합계 ----------

    public AccountBalanceSummaryResponse getBalanceSummary(Long userId) {
        List<Account> cashAccounts = accountRepository.findAllByUserId(userId).stream()
                .filter(account -> account.getStatus() != AccountStatus.CLOSED)
                .filter(account -> account.getAccountType() == AccountType.DEMAND_DEPOSIT)
                .sorted(Comparator.comparingLong(Account::getBalance).reversed().thenComparing(Account::getId))
                .toList();
        long totalCash = cashAccounts.stream().mapToLong(Account::getBalance).sum();

        List<AccountBalanceSummaryResponse.Item> items = cashAccounts.stream()
                .map(account -> new AccountBalanceSummaryResponse.Item(
                        account.getId(),
                        account.getBankName(),
                        account.getAccountNoMasked(),
                        account.getAccountName(),
                        account.getStatus().name(),
                        account.getBalance(),
                        sharePercent(account.getBalance(), totalCash)
                ))
                .toList();
        return new AccountBalanceSummaryResponse(totalCash, cashAccounts.size(), items);
    }

    // ---------- 휴면성 계좌 ----------

    /**
     * 기획안 확정 규칙 "잔액 > 0 AND 최근 N개월간 입출금 거래 없음"을 수시입출금 계좌에 적용한다.
     * 마지막 거래일이 없으면(거래 이력 미적재) 계좌 적재일을 마지막 활동일로 본다. 그래야 방금 연동한 새 계좌가 휴면으로 잡히지 않는다.
     *
     * @param inactiveMonths null이면 {@value DEFAULT_INACTIVE_MONTHS}
     * @param maxBalance     null이면 잔액 상한 없음
     */
    public InactiveAccountsResponse getInactiveAccounts(Long userId, Integer inactiveMonths, Long maxBalance) {
        int months = inactiveMonths == null ? DEFAULT_INACTIVE_MONTHS : inactiveMonths;
        LocalDate asOf = accountClock.now().toLocalDate();
        LocalDate threshold = asOf.minusMonths(months);

        List<InactiveAccountsResponse.Item> items = new ArrayList<>();
        for (Account account : accountRepository.findAllByUserId(userId)) {
            if (account.getStatus() == AccountStatus.CLOSED
                    || account.getAccountType() != AccountType.DEMAND_DEPOSIT
                    || account.getBalance() <= 0
                    || (maxBalance != null && account.getBalance() > maxBalance)) {
                continue;
            }
            LocalDate lastActivity = lastActivityDate(account);
            if (lastActivity.isAfter(threshold)) {
                continue;
            }
            items.add(new InactiveAccountsResponse.Item(
                    account.getId(),
                    account.getBankName(),
                    account.getAccountNoMasked(),
                    account.getAccountName(),
                    account.getStatus().name(),
                    account.getBalance(),
                    lastActivity,
                    ChronoUnit.MONTHS.between(lastActivity, asOf)
            ));
        }
        items.sort(Comparator.comparing(InactiveAccountsResponse.Item::lastActivityDate)
                .thenComparing(InactiveAccountsResponse.Item::accountId));

        return new InactiveAccountsResponse(
                new InactiveAccountsResponse.Criteria(months, maxBalance, asOf),
                items.size(),
                items.stream().mapToLong(InactiveAccountsResponse.Item::balance).sum(),
                items
        );
    }

    // ---------- 월별 입출금 요약·추이 ----------

    public AccountMonthlySummaryResponse getMonthlySummary(Long userId, String accountNo, String yearMonth) {
        Account account = findOwned(userId, accountNo);
        YearMonth month = yearMonthPolicy.resolveHistorical(yearMonth, accountClock.now().toLocalDate());

        AccountMonthlyFlowRow row = accountFlowReader
                .readMonthlyFlows(userId, account.getId(), month.atDay(1), month.atEndOfMonth())
                .stream()
                .findFirst()
                .orElse(AccountMonthlyFlowRow.empty(month));
        return AccountMonthlySummaryResponse.from(account, row);
    }

    /**
     * 기간(기본 최근 3개월, 최대 12개월) 안의 달을 모두 채운다. 거래가 없는 달은 0이다.
     */
    public AccountTrendResponse getTrend(Long userId, String accountNo, String startMonth, String endMonth) {
        Account account = findOwned(userId, accountNo);
        YearMonthPeriod period = yearMonthPeriodPolicy.resolve(startMonth, endMonth);

        Map<YearMonth, AccountMonthlyFlowRow> byMonth = accountFlowReader
                .readMonthlyFlows(userId, account.getId(), period.startMonth().atDay(1), period.endMonth().atEndOfMonth())
                .stream()
                .collect(Collectors.toMap(AccountMonthlyFlowRow::yearMonth, Function.identity()));

        List<AccountMonthlyFlowRow> rows = new ArrayList<>();
        for (YearMonth month = period.startMonth(); !month.isAfter(period.endMonth()); month = month.plusMonths(1)) {
            rows.add(byMonth.getOrDefault(month, AccountMonthlyFlowRow.empty(month)));
        }
        return AccountTrendResponse.from(account, period, rows);
    }

    // ---------- helpers ----------

    private Account findOwned(Long userId, String accountNo) {
        return accountRepository
                .findByUserIdAndAccountNoHash(userId, sensitiveNumberHasher.hash(accountNo))
                .orElseThrow(() -> new ResourceNotFoundException(AccountQueryService.ACCOUNT_NOT_FOUND_MESSAGE));
    }

    private static LocalDate lastActivityDate(Account account) {
        return account.getLastTransactionDate() != null
                ? account.getLastTransactionDate()
                : account.getCreatedAt().toLocalDate();
    }

    private static long balanceOf(List<Account> accounts, AccountType type) {
        return accounts.stream().filter(account -> account.getAccountType() == type).mapToLong(Account::getBalance).sum();
    }

    private static long countOf(List<Account> accounts, AccountType type) {
        return accounts.stream().filter(account -> account.getAccountType() == type).count();
    }

    private static long countByStatus(List<Account> accounts, AccountStatus status) {
        return accounts.stream().filter(account -> account.getStatus() == status).count();
    }

    private static double sharePercent(long balance, long total) {
        if (total <= 0 || balance <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(balance)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
