package com.ssafy.dodam.domain.account.service;

import com.ssafy.dodam.domain.account.entity.Account;
import com.ssafy.dodam.domain.account.entity.Account.FinanceSnapshot;
import com.ssafy.dodam.domain.account.entity.AccountType;
import com.ssafy.dodam.domain.account.repository.AccountRepository;
import com.ssafy.dodam.domain.account.repository.JdbcAccountTransactionWriter;
import com.ssafy.dodam.domain.account.repository.JdbcAccountTransactionWriter.NewTransaction;
import com.ssafy.dodam.domain.finance.entity.FinanceSyncScope;
import com.ssafy.dodam.domain.finance.service.FinanceSyncScopeHandler;
import com.ssafy.dodam.domain.finance.service.FinanceSyncScopeResult;
import com.ssafy.dodam.domain.goal.repository.SafeboxAccountRepository;
import com.ssafy.dodam.global.crypto.FieldCryptor;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.policy.QueryPeriodPolicy;
import com.ssafy.dodam.global.repository.UserWriteLock;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyAccountTransaction;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyApiException;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyDemandDepositAccount;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyFinanceClient;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyTransactionHistory;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyUnavailableException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Finance Data 동기화(499)의 ACCOUNT scope 구현. 금융망 수시입출금 계좌 목록과 각 계좌의 거래내역을
 * `account`·`transaction`에 반영한다(S15P21D105-501 계좌, S15P21D105-502 거래내역 Adapter).
 *
 * <p>규칙
 * <ul>
 *   <li>계좌 목록·거래내역 중 하나라도 실패하면 scope 전체가 FAILED고 부분 반영은 없다.
 *       잔액 조회부터 저장까지 사용자 행 잠금을 유지해 이체·배정과 순서를 맞춘다.</li>
 *   <li>계좌는 `(user_id, account_no_hash)`로 대응한다. 계좌번호 원문은 암호문·해시·마스킹으로만 남기고 로그에 쓰지 않는다.</li>
 *   <li>새 계좌는 `source_type = SSAFY`, ACTIVE로 넣는다. 기존 SSAFY 계좌는 최신 값으로 갱신하고 값이 같으면 건드리지 않는다.</li>
 *   <li>`source_type`이 SEED·MANUAL인 계좌는 계좌도 거래도 덮어쓰지 않는다. 시연 데이터와 마이너스통장 상환(534) 결과를 보존한다.</li>
 *   <li>우리 DB에 SSAFY 출처 수시입출금 계좌로 있는데 금융망 목록에서 사라진 계좌는 CLOSED로 바꾼다. 다시 나타나면 ACTIVE로 되돌린다.</li>
 *   <li>거래는 금융망에서 최근 {@value QueryPeriodPolicy#MAX_PERIOD_MONTHS}개월(조회 정책 최대 기간)을 받아 **없는 거래만**
 *       INSERT한다. 사용자가 바꾼 메모·카테고리·숨김은 보존된다.</li>
 *   <li>`syncedCount`는 계좌 추가·갱신·해지 + 거래 INSERT 건수이고, 내역은 `message`에 나눠 적는다.</li>
 * </ul>
 *
 * <p>금융망 오류는 {@link BusinessException}(FINANCE_API_ERROR / FINANCE_API_UNAVAILABLE)으로 바꿔 던진다.
 * {@code FinanceSyncService}가 이 scope만 FAILED로 기록하고 사용자에게 같은 메시지를 보여준다.
 */
@Component
public class AccountSyncHandler implements FinanceSyncScopeHandler {

    private static final Logger log = LoggerFactory.getLogger(AccountSyncHandler.class);
    private static final DateTimeFormatter FINANCE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter FINANCE_TIME = DateTimeFormatter.ofPattern("HHmmss");
    private static final String DEFAULT_CURRENCY = "KRW";
    private static final String FINANCE_DEPOSIT = "1";
    private static final String FINANCE_WITHDRAWAL = "2";

    private final SsafyFinanceClient ssafyFinanceClient;
    private final AccountRepository accountRepository;
    private final JdbcAccountTransactionWriter transactionWriter;
    private final FieldCryptor fieldCryptor;
    private final SensitiveNumberHasher sensitiveNumberHasher;
    private final AccountClock accountClock;
    private final TransactionTemplate transactionTemplate;
    private final UserWriteLock userWriteLock;
    private final SafeboxAccountRepository safeboxAccounts;

    public AccountSyncHandler(
            SsafyFinanceClient ssafyFinanceClient,
            AccountRepository accountRepository,
            JdbcAccountTransactionWriter transactionWriter,
            FieldCryptor fieldCryptor,
            SensitiveNumberHasher sensitiveNumberHasher,
            AccountClock accountClock,
            TransactionTemplate transactionTemplate,
            UserWriteLock userWriteLock,
            SafeboxAccountRepository safeboxAccounts
    ) {
        this.ssafyFinanceClient = ssafyFinanceClient;
        this.accountRepository = accountRepository;
        this.transactionWriter = transactionWriter;
        this.fieldCryptor = fieldCryptor;
        this.sensitiveNumberHasher = sensitiveNumberHasher;
        this.accountClock = accountClock;
        this.transactionTemplate = transactionTemplate;
        this.userWriteLock = userWriteLock;
        this.safeboxAccounts = safeboxAccounts;
    }

    @Override
    public FinanceSyncScope scope() {
        return FinanceSyncScope.ACCOUNT;
    }

    @Override
    public FinanceSyncScopeResult sync(Long userId, String userKey) {
        SyncCounts counts = Objects.requireNonNullElse(
                transactionTemplate.execute(status -> {
                    // 금융망 조회 시점부터 저장까지 잠가 이체·배정이 오래된 잔액을 기준으로 진행되지 않게 한다.
                    userWriteLock.lock(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
                    return apply(userId, fetch(userId, userKey));
                }),
                SyncCounts.NONE
        );
        return new FinanceSyncScopeResult(
                scope(),
                FinanceSyncScopeResult.Status.COMPLETED,
                counts.total(),
                "계좌 " + counts.accounts() + "건, 거래 " + counts.transactions() + "건 반영"
        );
    }

    // ---------- 금융망 호출·해석 ----------

    /**
     * 계좌 목록과 계좌별 거래내역을 받아 해석까지 끝낸다. 실패하면 저장 전에 트랜잭션 전체를 되돌린다.
     */
    private List<FinanceAccount> fetch(Long userId, String userKey) {
        LocalDate today = accountClock.now().toLocalDate();
        LocalDate historyStart = today.minusMonths(QueryPeriodPolicy.MAX_PERIOD_MONTHS).plusDays(1);
        try {
            List<SsafyDemandDepositAccount> accounts = ssafyFinanceClient.inquireDemandDepositAccountList(userKey);
            if (accounts == null) {
                return List.of();
            }
            List<FinanceAccount> result = new ArrayList<>();
            Set<String> seenAccountNos = new HashSet<>();
            for (SsafyDemandDepositAccount account : accounts) {
                if (account.accountNo() == null || account.accountNo().isBlank()) {
                    log.warn("SSAFY account list contained an entry without accountNo for user {}", userId);
                    continue;
                }
                String accountNo = account.accountNo().strip();
                if (!seenAccountNos.add(accountNo)) {
                    continue;
                }
                SsafyTransactionHistory history = ssafyFinanceClient.inquireTransactionHistoryList(
                        userKey, accountNo, historyStart, today
                );
                result.add(new FinanceAccount(accountNo, toSnapshot(account), toTransactions(history, userId)));
            }
            return result;
        } catch (SsafyApiException exception) {
            log.warn("SSAFY finance API error during account sync: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.FINANCE_API_ERROR);
        } catch (SsafyUnavailableException exception) {
            log.warn("SSAFY finance API unavailable during account sync: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.FINANCE_API_UNAVAILABLE);
        }
    }

    private static FinanceSnapshot toSnapshot(SsafyDemandDepositAccount financeAccount) {
        return new FinanceSnapshot(
                requireText(financeAccount.bankCode(), "bankCode"),
                requireText(financeAccount.bankName(), "bankName"),
                requireText(financeAccount.accountName(), "accountName"),
                AccountType.DEMAND_DEPOSIT,
                Objects.requireNonNullElse(financeAccount.accountBalance(), 0L),
                parseDate(financeAccount.lastTransactionDate()),
                financeAccount.currency() == null || financeAccount.currency().isBlank()
                        ? DEFAULT_CURRENCY
                        : financeAccount.currency().strip()
        );
    }

    private static List<SyncedTransaction> toTransactions(SsafyTransactionHistory history, Long userId) {
        if (history == null || history.list() == null) {
            return List.of();
        }
        List<SyncedTransaction> transactions = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (SsafyAccountTransaction entry : history.list()) {
            String transactionId = requireText(entry.transactionUniqueNo(), "transactionUniqueNo");
            if (!seenIds.add(transactionId)) {
                continue;
            }
            long amount = Objects.requireNonNullElse(entry.transactionBalance(), 0L);
            if (amount <= 0) {
                // ERD chk_transaction_amount(amount > 0). 0원 거래는 원장에 넣을 수 없어 건너뛴다.
                log.warn("SSAFY transaction {} for user {} has non-positive amount; skipped", transactionId, userId);
                continue;
            }
            transactions.add(new SyncedTransaction(new NewTransaction(
                    transactionId,
                    Objects.requireNonNull(parseDate(requireText(entry.transactionDate(), "transactionDate"))),
                    parseTime(entry.transactionTime()),
                    toTransactionType(entry.transactionType()),
                    amount,
                    entry.transactionAfterBalance(),
                    blankToNull(entry.transactionSummary()),
                    blankToNull(entry.transactionMemo())
            ), blankToNull(entry.transactionAccountNo())));
        }
        return transactions;
    }

    /**
     * 금융망 거래 구분("1" 입금, "2" 출금)을 ERD `transaction_type`으로 바꾼다. 그 외 값은 계약 위반으로 본다.
     */
    private static String toTransactionType(String financeType) {
        if (FINANCE_DEPOSIT.equals(financeType)) {
            return "INCOME";
        }
        if (FINANCE_WITHDRAWAL.equals(financeType)) {
            return "EXPENSE";
        }
        throw new SsafyUnavailableException("SSAFY transaction has an unknown transactionType: " + financeType, null);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new SsafyUnavailableException("SSAFY response entry is missing " + field, null);
        }
        return value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip(), FINANCE_DATE);
        } catch (DateTimeParseException exception) {
            throw new SsafyUnavailableException("SSAFY response has an unparseable date: " + value, null);
        }
    }

    private static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.strip(), FINANCE_TIME);
        } catch (DateTimeParseException exception) {
            throw new SsafyUnavailableException("SSAFY response has an unparseable time: " + value, null);
        }
    }

    // ---------- 저장 (트랜잭션 안) ----------

    private SyncCounts apply(Long userId, List<FinanceAccount> financeAccounts) {
        LocalDateTime now = accountClock.now();
        Map<String, Account> existingByHash = new HashMap<>();
        for (Account account : accountRepository.findAllByUserId(userId)) {
            existingByHash.put(account.getAccountNoHash(), account);
        }

        int accountChanges = 0;
        int transactionInserts = 0;
        Set<String> seenHashes = new HashSet<>();
        List<FinanceAccount> transactionTargets = new ArrayList<>();
        Map<String, Account> accountsByHash = new HashMap<>(existingByHash);
        Long safeboxAccountId = safeboxAccounts.findById(userId).map(ref -> ref.getAccountId()).orElse(null);

        for (FinanceAccount financeAccount : financeAccounts) {
            String hash = sensitiveNumberHasher.hash(financeAccount.accountNo());
            if (!seenHashes.add(hash)) {
                continue;
            }
            Account existing = existingByHash.get(hash);
            if (existing == null) {
                Account created = accountRepository.save(Account.fromFinance(
                        userId,
                        financeAccount.snapshot(),
                        fieldCryptor.encrypt(financeAccount.accountNo()),
                        hash,
                        AccountNumberMasker.mask(financeAccount.accountNo()),
                        now
                ));
                accountsByHash.put(hash, created);
                accountChanges++;
                transactionTargets.add(financeAccount);
            } else if (existing.isFromFinance()) {
                FinanceSnapshot snapshot = financeAccount.snapshot();
                if (existing.getId().equals(safeboxAccountId)) {
                    // 도담 전용 계좌 이름은 금융망 상품명(예: 수시입출금)으로 덮지 않는다(705).
                    snapshot = new FinanceSnapshot(snapshot.bankCode(), snapshot.bankName(), existing.getAccountName(),
                            snapshot.accountType(), snapshot.balance(), snapshot.lastTransactionDate(),
                            snapshot.currency());
                }
                if (existing.applyFinanceSnapshot(snapshot, now)) {
                    accountChanges++;
                }
                transactionTargets.add(financeAccount);
            } else {
                log.debug("Account {} of user {} is {} sourced; finance snapshot and transactions ignored",
                        existing.getId(), userId, existing.getSourceType());
            }
        }

        for (Account account : existingByHash.values()) {
            if (account.isFromFinance()
                    && account.getAccountType() == AccountType.DEMAND_DEPOSIT
                    && !seenHashes.contains(account.getAccountNoHash())
                    && account.close(now)) {
                accountChanges++;
            }
        }

        // 새 계좌의 account_id가 확정되고 FK 대상 행이 DB에 있어야 거래를 넣을 수 있다.
        accountRepository.flush();
        for (FinanceAccount target : transactionTargets) {
            Account account = accountsByHash.get(sensitiveNumberHasher.hash(target.accountNo()));
            List<NewTransaction> transactions = target.transactions().stream()
                    .map(entry -> {
                        String counterparty = entry.counterpartyAccountNo();
                        if (counterparty == null) {
                            return entry.transaction();
                        }
                        String counterpartyHash = sensitiveNumberHasher.hash(counterparty);
                        return accountsByHash.containsKey(counterpartyHash)
                                && !counterpartyHash.equals(account.getAccountNoHash())
                                ? entry.transaction().asInternalTransfer() : entry.transaction();
                    })
                    .toList();
            transactionInserts += transactionWriter.insertMissing(userId, account.getId(), transactions, now);
        }
        return new SyncCounts(accountChanges, transactionInserts);
    }

    /**
     * 해석이 끝난 금융망 계좌 1건과 그 거래내역. 계좌번호 원문은 암호화·해시·마스킹 입력으로만 쓴다.
     */
    private record FinanceAccount(String accountNo, FinanceSnapshot snapshot, List<SyncedTransaction> transactions) {
    }

    // 상대 계좌번호는 동기화 중 HMAC 비교에만 사용하며 원장·로그에 남기지 않는다.
    private record SyncedTransaction(NewTransaction transaction, String counterpartyAccountNo) {
    }

    private record SyncCounts(int accounts, int transactions) {
        static final SyncCounts NONE = new SyncCounts(0, 0);

        int total() {
            return accounts + transactions;
        }
    }
}
