package com.ssafy.dodam.domain.goal.service;

import com.ssafy.dodam.domain.account.entity.Account;
import com.ssafy.dodam.domain.account.entity.Account.FinanceSnapshot;
import com.ssafy.dodam.domain.account.entity.AccountStatus;
import com.ssafy.dodam.domain.account.entity.AccountType;
import com.ssafy.dodam.domain.account.repository.AccountRepository;
import com.ssafy.dodam.domain.account.service.AccountClock;
import com.ssafy.dodam.domain.account.service.AccountNumberMasker;
import com.ssafy.dodam.domain.finance.service.FinanceConnectionService;
import com.ssafy.dodam.domain.goal.dto.response.SafeboxAccountResponse;
import com.ssafy.dodam.domain.goal.entity.SafeboxAccount;
import com.ssafy.dodam.domain.goal.repository.SafeboxAccountRepository;
import com.ssafy.dodam.global.crypto.FieldCryptor;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.repository.UserWriteLock;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyApiException;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyCreatedDemandDepositAccount;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyDemandDepositProduct;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyFinanceClient;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyUnavailableException;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** SSAFY 금융망 계좌 한 개를 도담 전용 계좌로 개설하고 사용자에 묶는다. */
@Service
public class SafeboxAccountService {
    static final String ACCOUNT_NAME = "도담 계좌";

    private final SafeboxAccountRepository refs;
    private final AccountRepository accounts;
    private final SafeboxAllocationService allocations;
    private final FinanceConnectionService connections;
    private final SsafyFinanceClient finance;
    private final FieldCryptor cryptor;
    private final SensitiveNumberHasher hasher;
    private final UserWriteLock userLock;
    private final AccountClock clock;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public SafeboxAccountService(SafeboxAccountRepository refs, AccountRepository accounts,
            SafeboxAllocationService allocations, FinanceConnectionService connections,
            SsafyFinanceClient finance, FieldCryptor cryptor, SensitiveNumberHasher hasher,
            UserWriteLock userLock, AccountClock clock, JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.refs = refs;
        this.accounts = accounts;
        this.allocations = allocations;
        this.connections = connections;
        this.finance = finance;
        this.cryptor = cryptor;
        this.hasher = hasher;
        this.userLock = userLock;
        this.clock = clock;
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public SafeboxAccountResponse get(Long userId) {
        Account account = requireAccount(userId);
        long total = allocations.totalAllocated(userId, account.getId());
        return response(account, total);
    }

    /** 금융망 개설 전에 시도 기록을 별도로 확정해 응답 불명확·DB 저장 실패 후 중복 개설을 막는다. */
    public SafeboxAccountResponse open(Long userId) {
        if (refs.existsById(userId)) {
            return get(userId);
        }
        String userKey;
        try {
            userKey = connections.userKeyOf(connections.requireConnected(userId));
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != ErrorCode.FINANCE_NOT_CONNECTED) {
                throw exception;
            }
            // 목표 생성 = 세이프박스 생성이므로 미연결 사용자는 여기서 연결한다. 연결 이벤트가 계좌를 먼저 열었으면 그대로 반환한다.
            connections.connect(userId);
            if (refs.existsById(userId)) {
                return get(userId);
            }
            userKey = connections.userKeyOf(connections.requireConnected(userId));
        }
        SsafyDemandDepositProduct selected;
        try {
            List<SsafyDemandDepositProduct> products = finance.inquireDemandDepositList();
            if (products == null) {
                throw new BusinessException(ErrorCode.FINANCE_API_UNAVAILABLE);
            }
            selected = products.stream()
                    .filter(item -> item.accountTypeUniqueNo() != null && !item.accountTypeUniqueNo().isBlank())
                    .min(Comparator.comparing(SsafyDemandDepositProduct::accountTypeUniqueNo))
                    .orElseThrow(() -> new BusinessException(ErrorCode.FINANCE_API_UNAVAILABLE));
        } catch (SsafyApiException exception) {
            throw new BusinessException(ErrorCode.FINANCE_API_ERROR);
        } catch (SsafyUnavailableException exception) {
            throw new BusinessException(ErrorCode.FINANCE_API_UNAVAILABLE);
        }
        SafeboxAccountResponse existing = transactions.execute(status -> {
            userLock.lock(userId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            if (refs.existsById(userId)) {
                return get(userId);
            }
            Integer pending = jdbc.queryForObject("SELECT COUNT(*) FROM safebox_open_attempt WHERE user_id = ?",
                    Integer.class, userId);
            if (pending != null && pending > 0) {
                throw new BusinessException(ErrorCode.SAFEBOX_OPEN_RECONCILIATION_REQUIRED);
            }
            jdbc.update("INSERT INTO safebox_open_attempt (user_id, created_at) VALUES (?, ?)",
                    userId, clock.now());
            return null;
        });
        if (existing != null) {
            return existing;
        }
        SsafyCreatedDemandDepositAccount created;
        try {
            created = finance.createDemandDepositAccount(userKey, selected.accountTypeUniqueNo());
        } catch (SsafyApiException exception) {
            // 금융망이 오류 코드로 거절했으면 계좌가 생기지 않았다. 시도 기록을 지워 재시도를 허용한다.
            transactions.executeWithoutResult(status ->
                    jdbc.update("DELETE FROM safebox_open_attempt WHERE user_id = ?", userId));
            throw new BusinessException(ErrorCode.FINANCE_API_ERROR);
        } catch (SsafyUnavailableException exception) {
            throw new BusinessException(ErrorCode.FINANCE_API_UNAVAILABLE);
        }
        return transactions.execute(status -> completeOpen(userId, created, selected));
    }

    /**
     * 생성 응답은 은행 코드와 계좌번호만 준다. 은행명·상품명은 고른 상품에서 채우고 잔액은 신규 계좌이므로 0이다.
     * 이후 계좌 동기화가 금융망 목록 값으로 갱신한다. 계좌번호가 없으면 개설 여부를 알 수 없어 시도 기록을 남긴다.
     */
    private SafeboxAccountResponse completeOpen(Long userId, SsafyCreatedDemandDepositAccount created,
            SsafyDemandDepositProduct product) {
        userLock.lock(userId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (created == null || created.accountNo() == null
                || !created.accountNo().strip().matches("[0-9]{10,20}")) {
            throw new BusinessException(ErrorCode.FINANCE_API_UNAVAILABLE);
        }
        String bankCode = firstNonBlank(created.bankCode(), product.bankCode());
        String bankName = firstNonBlank(product.bankName(), bankCode);
        // 상품 유형명(예: 수시입출금)은 다른 입출금 계좌와 구분되지 않아 화면 이름을 고정한다. 동기화도 이 이름을 유지한다.
        String accountName = ACCOUNT_NAME;
        if (bankCode == null) {
            throw new BusinessException(ErrorCode.FINANCE_API_UNAVAILABLE);
        }
        String accountNo = created.accountNo().strip();
        String hash = hasher.hash(accountNo);
        accounts.findByUserIdAndAccountNoHash(userId, hash).ifPresent(existing -> {
            if (!existing.isFromFinance()) {
                throw new BusinessException(ErrorCode.FINANCE_API_ERROR);
            }
        });
        Account account = accounts.findByUserIdAndAccountNoHash(userId, hash).orElseGet(() -> accounts.save(
                Account.fromFinance(userId, new FinanceSnapshot(bankCode, bankName, accountName,
                                AccountType.DEMAND_DEPOSIT, 0L, null, "KRW"),
                        cryptor.encrypt(accountNo), hash, AccountNumberMasker.mask(accountNo), clock.now())));
        refs.save(new SafeboxAccount(userId, account.getId(), clock.now()));
        jdbc.update("DELETE FROM safebox_open_attempt WHERE user_id = ?", userId);
        return response(account, 0L);
    }

    private Account requireAccount(Long userId) {
        SafeboxAccount ref = refs.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SAFEBOX_ACCOUNT_NOT_FOUND));
        Account account = accounts.findByIdAndUserId(ref.getAccountId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SAFEBOX_ACCOUNT_NOT_FOUND));
        if (account.getAccountType() != AccountType.DEMAND_DEPOSIT || account.getStatus() == AccountStatus.CLOSED) {
            throw new BusinessException(ErrorCode.SAFEBOX_ACCOUNT_NOT_FOUND);
        }
        return account;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.strip();
        }
        return second == null || second.isBlank() ? null : second.strip();
    }

    private static SafeboxAccountResponse response(Account account, long total) {
        return new SafeboxAccountResponse(account.getId(), account.getAccountNoMasked(), account.getBalance(), total,
                Math.max(0L, account.getBalance() - total), Math.max(0L, total - account.getBalance()));
    }
}
