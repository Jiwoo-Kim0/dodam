package com.ssafy.dodam.domain.account.service;

import com.ssafy.dodam.domain.account.dto.request.AccountTransferRequest;
import com.ssafy.dodam.domain.account.dto.response.AccountTransferResponse;
import com.ssafy.dodam.domain.account.entity.Account;
import com.ssafy.dodam.domain.account.entity.AccountStatus;
import com.ssafy.dodam.domain.account.entity.AccountType;
import com.ssafy.dodam.domain.account.repository.AccountRepository;
import com.ssafy.dodam.domain.account.repository.JdbcAccountTransactionWriter;
import com.ssafy.dodam.domain.account.repository.JdbcAccountTransactionWriter.NewTransaction;
import com.ssafy.dodam.domain.finance.service.FinanceConnectionService;
import com.ssafy.dodam.domain.goal.repository.SafeboxAccountRepository;
import com.ssafy.dodam.domain.goal.service.SafeboxAllocationService;
import com.ssafy.dodam.global.crypto.FieldCryptor;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.repository.UserWriteLock;
import com.ssafy.dodam.global.repository.SafeboxOperationLock;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyApiException;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyDepositResult;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyErrorCode;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyFinanceClient;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyTransferEntry;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyUnavailableException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountTransferService {

    private static final Logger log = LoggerFactory.getLogger(AccountTransferService.class);
    private static final Duration DUPLICATE_WINDOW = Duration.ofSeconds(10);
    private static final DateTimeFormatter FINANCE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter FINANCE_TIME = DateTimeFormatter.ofPattern("HHmmss");
    private static final String WITHDRAWAL_SUMMARY = "계좌 이체";
    private static final String DEPOSIT_SUMMARY = "계좌 이체 입금";

    private final AccountRepository accounts;
    private final FinanceConnectionService connections;
    private final SsafyFinanceClient finance;
    private final FieldCryptor cryptor;
    private final UserWriteLock userWriteLock;
    private final JdbcAccountTransactionWriter transactions;
    private final JdbcTemplate jdbc;
    private final AccountClock clock;
    private final TransactionTemplate transactionTemplate;
    private final SafeboxAccountRepository safeboxAccounts;
    private final SafeboxAllocationService safeboxAllocations;
    private final SafeboxOperationLock operationLock;
    private final ConcurrentHashMap<TransferKey, LocalDateTime> recent = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, PendingReconciliation> pendingReconciliation = new ConcurrentHashMap<>();
    private final AtomicLong reconciliationVersion = new AtomicLong();

    public AccountTransferService(AccountRepository accounts, FinanceConnectionService connections,
            SsafyFinanceClient finance, FieldCryptor cryptor, UserWriteLock userWriteLock,
            JdbcAccountTransactionWriter transactions, JdbcTemplate jdbc, AccountClock clock,
            TransactionTemplate transactionTemplate, SafeboxAccountRepository safeboxAccounts,
            SafeboxAllocationService safeboxAllocations, SafeboxOperationLock operationLock) {
        this.accounts = accounts;
        this.connections = connections;
        this.finance = finance;
        this.cryptor = cryptor;
        this.userWriteLock = userWriteLock;
        this.transactions = transactions;
        this.jdbc = jdbc;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
        this.safeboxAccounts = safeboxAccounts;
        this.safeboxAllocations = safeboxAllocations;
        this.operationLock = operationLock;
    }

    public AccountTransferResponse transfer(Long userId, AccountTransferRequest request) {
        ReentrantLock lock = operationLock.forUser(userId);
        lock.lock();
        AtomicBoolean financeSucceeded = new AtomicBoolean();
        try {
            // 금융망 호출까지 사용자 행 잠금을 유지해 다른 인스턴스의 배정·동기화와 순서를 맞춘다.
            return transactionTemplate.execute(status -> {
                userWriteLock.lock(userId).orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
                return transferLocked(userId, request, financeSucceeded);
            });
        } catch (RuntimeException exception) {
            if (financeSucceeded.get()) {
                pendingReconciliation.compute(userId, (ignored, old) -> {
                    Set<Long> ids = new java.util.HashSet<>(old == null ? Set.of() : old.accountIds());
                    ids.add(request.withdrawalAccountId());
                    ids.add(request.depositAccountId());
                    return new PendingReconciliation(Set.copyOf(ids), reconciliationVersion.incrementAndGet());
                });
                log.error("SSAFY transfer succeeded but local save failed for user {}", userId, exception);
            }
            throw exception;
        } finally {
            lock.unlock();
        }
    }

    private AccountTransferResponse transferLocked(Long userId, AccountTransferRequest request,
            AtomicBoolean financeSucceeded) {
        if (request.withdrawalAccountId().equals(request.depositAccountId()) || request.amount() <= 0) {
            throw new InvalidRequestException("서로 다른 계좌와 양수 금액을 지정해 주세요.");
        }
        Account withdrawal = ownedAccount(userId, request.withdrawalAccountId());
        Account deposit = ownedAccount(userId, request.depositAccountId());
        validateAccount(withdrawal);
        validateAccount(deposit);
        if (withdrawal.getBalance() < request.amount()) {
            throw new BusinessException(ErrorCode.TRANSFER_INSUFFICIENT_BALANCE);
        }
        if (safeboxAccounts.findById(userId)
                .map(ref -> ref.getAccountId().equals(withdrawal.getId())).orElse(false)
                && request.amount() > withdrawal.getBalance()
                        - safeboxAllocations.totalAllocated(userId, withdrawal.getId())) {
            throw new BusinessException(ErrorCode.SAFEBOX_ALLOCATED_BALANCE);
        }
        TransferKey key = new TransferKey(userId, withdrawal.getId(), deposit.getId(), request.amount());
        LocalDateTime now = clock.now();
        recent.entrySet().removeIf(entry -> Duration.between(entry.getValue(), now).compareTo(DUPLICATE_WINDOW) >= 0);
        LocalDateTime previous = recent.putIfAbsent(key, now);
        if (previous != null) {
            throw new BusinessException(ErrorCode.TRANSFER_DUPLICATE_REQUEST);
        }

        boolean fromFinance = withdrawal.isFromFinance();
        boolean toFinance = deposit.isFromFinance();
        if (!fromFinance && !toFinance) {
            // 두 계좌 모두 금융망에 없는 seed·수동 계좌면 도담 DB 안에서만 옮긴다.
            return save(userId, request, List.of(localEntry("EXPENSE", request.amount(), now),
                    localEntry("INCOME", request.amount(), now)));
        }
        String userKey = connections.userKeyOf(connections.requireConnected(userId));
        try {
            if (fromFinance && toFinance) {
                List<SsafyTransferEntry> entries = finance.transfer(userKey,
                        cryptor.decrypt(withdrawal.getAccountNoEncrypted()),
                        cryptor.decrypt(deposit.getAccountNoEncrypted()), request.amount(),
                        WITHDRAWAL_SUMMARY, DEPOSIT_SUMMARY);
                financeSucceeded.set(true);
                return save(userId, request, parseEntries(entries, request.amount(), now));
            }
            /*
             * 한쪽만 금융망 계좌(예: seed 주거래 → 도담 계좌)면 금융망 이체(2.4.10)가 불가능하다. 금융망 쪽은 입금(2.4.9)
             * 또는 출금(2.4.8)으로 실제로 움직이고, seed 쪽은 도담 DB에서만 반영한다. seed 계좌는 동기화가 덮지 않으므로
             * 도담 DB 잔액이 기준이다(S15P21D105-705).
             */
            SsafyDepositResult result = fromFinance
                    ? finance.withdrawFromAccount(userKey,
                            cryptor.decrypt(withdrawal.getAccountNoEncrypted()), request.amount(), WITHDRAWAL_SUMMARY)
                    : finance.depositToAccount(userKey,
                            cryptor.decrypt(deposit.getAccountNoEncrypted()), request.amount(), DEPOSIT_SUMMARY);
            // 응답 해석이 실패해도 금융망에서는 이미 움직였으므로 잔액 대조 대상으로 남긴다.
            financeSucceeded.set(true);
            NewTransaction financeSide = fromDepositResult(result, fromFinance ? "EXPENSE" : "INCOME",
                    request.amount(), now);
            NewTransaction localSide = localEntry(fromFinance ? "INCOME" : "EXPENSE", request.amount(), now);
            return save(userId, request, fromFinance ? List.of(financeSide, localSide) : List.of(localSide, financeSide));
        } catch (SsafyApiException exception) {
            recent.remove(key, now);
            if (exception.is(SsafyErrorCode.INSUFFICIENT_BALANCE)) {
                throw new BusinessException(ErrorCode.TRANSFER_INSUFFICIENT_BALANCE);
            }
            if (exception.is(SsafyErrorCode.ONE_TIME_TRANSFER_LIMIT)
                    || exception.is(SsafyErrorCode.DAILY_TRANSFER_LIMIT)) {
                throw new BusinessException(ErrorCode.TRANSFER_LIMIT_EXCEEDED);
            }
            log.warn("SSAFY transfer rejected with code {}", exception.getResponseCode());
            throw new BusinessException(ErrorCode.FINANCE_API_ERROR);
        } catch (SsafyUnavailableException exception) {
            // 도달 여부가 불명확한 타임아웃은 중복 창 동안 재시도를 막는다.
            log.warn("SSAFY transfer unavailable");
            throw new BusinessException(ErrorCode.FINANCE_API_UNAVAILABLE);
        }
    }

    /** 금융망을 거치지 않는 쪽의 거래. 금융망 거래번호와 겹치지 않게 접두어를 붙인다. */
    private static NewTransaction localEntry(String type, long amount, LocalDateTime now) {
        return new NewTransaction("LOCAL-" + java.util.UUID.randomUUID(), now.toLocalDate(),
                now.toLocalTime().withNano(0), type, amount, null,
                "EXPENSE".equals(type) ? WITHDRAWAL_SUMMARY : DEPOSIT_SUMMARY, null);
    }

    /*
     * 실제 금융망 성공 응답 REC는 입금·출금이 transactionUniqueNo·transactionDate뿐이고, 이체는 여기에 transactionType이
     * 더해질 뿐 시각·금액·잔액이 없다(2026-09-26 실호출 확인). 거래번호만 필수로 보고, 금액은 요청값, 시각은 도담 시계로
     * 채운다. 잔액은 save()가 도담 계좌 잔액으로 다시 쓴다.
     */
    private static NewTransaction fromDepositResult(SsafyDepositResult result, String type, long amount,
            LocalDateTime now) {
        if (result == null || result.transactionUniqueNo() == null || result.transactionUniqueNo().isBlank()) {
            throw new BusinessException(ErrorCode.FINANCE_API_UNAVAILABLE);
        }
        return new NewTransaction(result.transactionUniqueNo(), financeDate(result.transactionDate(), now),
                financeTime(result.transactionTime(), now), type, amount, result.transactionAfterBalance(),
                "EXPENSE".equals(type) ? WITHDRAWAL_SUMMARY : DEPOSIT_SUMMARY, null);
    }

    private static LocalDate financeDate(String value, LocalDateTime now) {
        try {
            return value == null || value.isBlank() ? now.toLocalDate() : LocalDate.parse(value, FINANCE_DATE);
        } catch (DateTimeParseException exception) {
            return now.toLocalDate();
        }
    }

    private static LocalTime financeTime(String value, LocalDateTime now) {
        try {
            return value == null || value.isBlank() ? now.toLocalTime().withNano(0) : LocalTime.parse(value, FINANCE_TIME);
        } catch (DateTimeParseException exception) {
            return now.toLocalTime().withNano(0);
        }
    }

    /** 금융망 이체 성공 뒤 로컬 저장이 실패한 계좌만 다음 계좌 조회에서 잔액을 대조한다. */
    public void reconcileIfPending(Long userId) {
        ReentrantLock lock = operationLock.forUser(userId);
        lock.lock();
        try {
            PendingReconciliation pending = transactionTemplate.execute(status -> reconcileLocked(userId));
            if (pending != null) {
                pendingReconciliation.remove(userId, pending);
            }
        } finally {
            lock.unlock();
        }
    }

    private PendingReconciliation reconcileLocked(Long userId) {
        userWriteLock.lock(userId).orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        PendingReconciliation pending = pendingReconciliation.get(userId);
        if (pending == null) {
            return null;
        }
        String userKey = connections.userKeyOf(connections.requireConnected(userId));
        Map<Long, Long> balances = new java.util.LinkedHashMap<>();
        try {
            for (Long accountId : pending.accountIds()) {
                Account account = ownedAccount(userId, accountId);
                if (!account.isFromFinance()) {
                    // seed·수동 계좌는 금융망에 없어 조회할 수 없고, 도담 DB 잔액이 기준이다.
                    continue;
                }
                var response = finance.inquireAccountBalance(userKey, cryptor.decrypt(account.getAccountNoEncrypted()));
                if (response == null || response.accountBalance() == null || response.accountBalance() < 0) {
                    throw new SsafyUnavailableException("SSAFY balance response invalid", null);
                }
                balances.put(accountId, response.accountBalance());
            }
        } catch (SsafyApiException exception) {
            log.warn("SSAFY balance recovery rejected with code {}", exception.getResponseCode());
            throw new BusinessException(ErrorCode.FINANCE_API_ERROR);
        } catch (SsafyUnavailableException exception) {
            log.warn("SSAFY balance recovery unavailable");
            throw new BusinessException(ErrorCode.FINANCE_API_UNAVAILABLE);
        }
        LocalDateTime now = clock.now();
        balances.forEach((accountId, balance) -> ownedAccount(userId, accountId).reconcileBalance(balance, now));
        return pending;
    }

    private AccountTransferResponse save(Long userId, AccountTransferRequest request, List<NewTransaction> entries) {
        userWriteLock.lock(userId).orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        Account withdrawal = ownedAccount(userId, request.withdrawalAccountId());
        Account deposit = ownedAccount(userId, request.depositAccountId());
        validateAccount(withdrawal);
        validateAccount(deposit);
        LocalDateTime now = clock.now();
        withdrawal.withdraw(request.amount(), now);
        deposit.deposit(request.amount(), now);
        accounts.flush();
        Long categoryId = jdbc.queryForObject("SELECT category_id FROM transaction_category WHERE code = ?",
                Long.class, "SAVING");
        if (categoryId == null) {
            throw new IllegalStateException("SAVING category missing");
        }
        NewTransaction out = entries.get(0);
        NewTransaction in = entries.get(1);
        int inserted = transactions.insertMissing(userId, withdrawal.getId(),
                List.of(categorize(out, withdrawal.getBalance(), categoryId)), now);
        inserted += transactions.insertMissing(userId, deposit.getId(),
                List.of(categorize(in, deposit.getBalance(), categoryId)), now);
        if (inserted != 2) {
            throw new IllegalStateException("SSAFY transfer transaction ID collision");
        }
        return new AccountTransferResponse(withdrawal.getId(), withdrawal.getBalance(), deposit.getId(),
                deposit.getBalance(), request.amount());
    }

    private static NewTransaction categorize(NewTransaction entry, long balance, Long categoryId) {
        return new NewTransaction(entry.transactionId(), entry.transactionDate(), entry.transactionTime(),
                entry.transactionType(), entry.amount(), balance, entry.summary(), null,
                categoryId, "NON_SPENDING");
    }

    private List<NewTransaction> parseEntries(List<SsafyTransferEntry> entries, long amount, LocalDateTime now) {
        if (entries == null || entries.size() != 2 || entries.get(0) == null || entries.get(1) == null) {
            throw new BusinessException(ErrorCode.FINANCE_API_UNAVAILABLE);
        }
        SsafyTransferEntry out = entries.stream().filter(entry -> "2".equals(entry.transactionType()))
                .findFirst().orElse(null);
        SsafyTransferEntry in = entries.stream().filter(entry -> "1".equals(entry.transactionType()))
                .findFirst().orElse(null);
        if (out == null || in == null
                || out.transactionUniqueNo() == null || in.transactionUniqueNo() == null
                || out.transactionUniqueNo().isBlank() || in.transactionUniqueNo().isBlank()
                || out.transactionUniqueNo().equals(in.transactionUniqueNo())
                || !amountMatches(out.transactionBalance(), amount)
                || !amountMatches(in.transactionBalance(), amount)) {
            throw new BusinessException(ErrorCode.FINANCE_API_UNAVAILABLE);
        }
        return List.of(toTransaction(out, "EXPENSE", amount, WITHDRAWAL_SUMMARY, now),
                toTransaction(in, "INCOME", amount, DEPOSIT_SUMMARY, now));
    }

    /** 실제 응답에는 금액이 없다. 문서 예시처럼 금액이 올 때만 요청값과 대조한다. */
    private static boolean amountMatches(Long responseAmount, long amount) {
        return responseAmount == null || responseAmount == amount;
    }

    private static NewTransaction toTransaction(SsafyTransferEntry entry, String type, long amount,
            String fallbackSummary, LocalDateTime now) {
        String summary = entry.transactionSummary() == null || entry.transactionSummary().isBlank()
                ? fallbackSummary : entry.transactionSummary();
        return new NewTransaction(entry.transactionUniqueNo(), financeDate(entry.transactionDate(), now),
                financeTime(entry.transactionTime(), now), type, amount, entry.transactionAfterBalance(), summary, null);
    }

    private Account ownedAccount(Long userId, Long accountId) {
        return accounts.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("계좌를 찾을 수 없습니다."));
    }

    private static void validateAccount(Account account) {
        if (account.getAccountType() != AccountType.DEMAND_DEPOSIT || account.getStatus() == AccountStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private record TransferKey(Long userId, Long withdrawalId, Long depositId, long amount) {
    }

    private record PendingReconciliation(Set<Long> accountIds, long version) {
    }
}
