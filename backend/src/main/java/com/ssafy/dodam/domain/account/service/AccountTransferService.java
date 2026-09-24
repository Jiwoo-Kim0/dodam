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
import com.ssafy.dodam.global.crypto.FieldCryptor;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.repository.UserWriteLock;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyApiException;
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
    private static final String WITHDRAWAL_SUMMARY = "목표 통장 이체";
    private static final String DEPOSIT_SUMMARY = "목표 저축 입금";

    private final AccountRepository accounts;
    private final FinanceConnectionService connections;
    private final SsafyFinanceClient finance;
    private final FieldCryptor cryptor;
    private final UserWriteLock userWriteLock;
    private final JdbcAccountTransactionWriter transactions;
    private final JdbcTemplate jdbc;
    private final AccountClock clock;
    private final TransactionTemplate transactionTemplate;
    private final ConcurrentHashMap<TransferKey, LocalDateTime> recent = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, PendingReconciliation> pendingReconciliation = new ConcurrentHashMap<>();
    private final AtomicLong reconciliationVersion = new AtomicLong();

    public AccountTransferService(AccountRepository accounts, FinanceConnectionService connections,
            SsafyFinanceClient finance, FieldCryptor cryptor, UserWriteLock userWriteLock,
            JdbcAccountTransactionWriter transactions, JdbcTemplate jdbc, AccountClock clock,
            TransactionTemplate transactionTemplate) {
        this.accounts = accounts;
        this.connections = connections;
        this.finance = finance;
        this.cryptor = cryptor;
        this.userWriteLock = userWriteLock;
        this.transactions = transactions;
        this.jdbc = jdbc;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    public AccountTransferResponse transfer(Long userId, AccountTransferRequest request) {
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
        String userKey = connections.userKeyOf(connections.requireConnected(userId));
        TransferKey key = new TransferKey(userId, withdrawal.getId(), deposit.getId(), request.amount());
        LocalDateTime now = clock.now();
        recent.entrySet().removeIf(entry -> Duration.between(entry.getValue(), now).compareTo(DUPLICATE_WINDOW) >= 0);
        LocalDateTime previous = recent.putIfAbsent(key, now);
        if (previous != null) {
            throw new BusinessException(ErrorCode.TRANSFER_DUPLICATE_REQUEST);
        }

        List<SsafyTransferEntry> entries;
        try {
            entries = finance.transfer(userKey, cryptor.decrypt(withdrawal.getAccountNoEncrypted()),
                    cryptor.decrypt(deposit.getAccountNoEncrypted()), request.amount(),
                    WITHDRAWAL_SUMMARY, DEPOSIT_SUMMARY);
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

        try {
            List<NewTransaction> parsed = parseEntries(entries, request.amount());
            return transactionTemplate.execute(status -> save(userId, request, parsed));
        } catch (RuntimeException exception) {
            pendingReconciliation.compute(userId, (ignored, old) -> {
                Set<Long> ids = new java.util.HashSet<>(old == null ? Set.of() : old.accountIds());
                ids.add(request.withdrawalAccountId());
                ids.add(request.depositAccountId());
                return new PendingReconciliation(Set.copyOf(ids), reconciliationVersion.incrementAndGet());
            });
            log.error("SSAFY transfer succeeded but local save failed for user {}", userId, exception);
            throw exception;
        }
    }

    /** 금융망 이체 성공 뒤 로컬 저장이 실패한 계좌만 다음 계좌 조회에서 잔액을 대조한다. */
    public void reconcileIfPending(Long userId) {
        PendingReconciliation pending = pendingReconciliation.get(userId);
        if (pending == null) {
            return;
        }
        String userKey = connections.userKeyOf(connections.requireConnected(userId));
        Map<Long, Long> balances = new java.util.LinkedHashMap<>();
        try {
            for (Long accountId : pending.accountIds()) {
                Account account = ownedAccount(userId, accountId);
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
        transactionTemplate.executeWithoutResult(status -> {
            userWriteLock.lock(userId).orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
            LocalDateTime now = clock.now();
            balances.forEach((accountId, balance) -> ownedAccount(userId, accountId).reconcileBalance(balance, now));
        });
        pendingReconciliation.remove(userId, pending);
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

    private List<NewTransaction> parseEntries(List<SsafyTransferEntry> entries, long amount) {
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
                || !Long.valueOf(amount).equals(out.transactionBalance())
                || !Long.valueOf(amount).equals(in.transactionBalance())) {
            throw new BusinessException(ErrorCode.FINANCE_API_UNAVAILABLE);
        }
        try {
            return List.of(toTransaction(out, "EXPENSE"), toTransaction(in, "INCOME"));
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.FINANCE_API_UNAVAILABLE);
        }
    }

    private static NewTransaction toTransaction(SsafyTransferEntry entry, String type) {
        return new NewTransaction(entry.transactionUniqueNo(),
                LocalDate.parse(entry.transactionDate(), FINANCE_DATE),
                LocalTime.parse(entry.transactionTime(), FINANCE_TIME), type,
                entry.transactionBalance(), entry.transactionAfterBalance(), entry.transactionSummary(), null);
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
