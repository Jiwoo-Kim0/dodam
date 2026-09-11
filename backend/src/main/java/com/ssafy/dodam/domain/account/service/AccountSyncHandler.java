package com.ssafy.dodam.domain.account.service;

import com.ssafy.dodam.domain.account.entity.Account;
import com.ssafy.dodam.domain.account.entity.Account.FinanceSnapshot;
import com.ssafy.dodam.domain.account.entity.AccountType;
import com.ssafy.dodam.domain.account.repository.AccountRepository;
import com.ssafy.dodam.domain.finance.entity.FinanceSyncScope;
import com.ssafy.dodam.domain.finance.service.FinanceSyncScopeHandler;
import com.ssafy.dodam.domain.finance.service.FinanceSyncScopeResult;
import com.ssafy.dodam.global.crypto.FieldCryptor;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyApiException;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyDemandDepositAccount;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyFinanceClient;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyUnavailableException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
 * Finance Data 동기화(499)의 ACCOUNT scope 구현. 금융망 수시입출금 계좌 목록을 `account`에 반영한다.
 *
 * <p>규칙
 * <ul>
 *   <li>금융망 호출은 트랜잭션 밖에서 하고 저장 구간만 트랜잭션을 연다({@link FinanceSyncScopeHandler} 계약).</li>
 *   <li>계좌는 `(user_id, account_no_hash)`로 대응한다. 계좌번호 원문은 암호문·해시·마스킹으로만 남기고 로그에 쓰지 않는다.</li>
 *   <li>새 계좌는 `source_type = SSAFY`, ACTIVE로 넣는다. 기존 SSAFY 계좌는 최신 값으로 갱신하고 값이 같으면 건드리지 않는다.</li>
 *   <li>`source_type`이 SEED·MANUAL인 계좌는 덮어쓰지 않는다. 시연 데이터와 마이너스통장 상환(534) 결과를 보존하기 위해서다.</li>
 *   <li>우리 DB에 SSAFY 출처 수시입출금 계좌로 있는데 금융망 목록에서 사라진 계좌는 CLOSED로 바꾼다. 다시 나타나면 ACTIVE로 되돌린다.</li>
 *   <li>`syncedCount`는 추가·갱신·해지로 실제 바뀐 행 수다.</li>
 * </ul>
 *
 * <p>금융망 오류는 {@link BusinessException}(FINANCE_API_ERROR / FINANCE_API_UNAVAILABLE)으로 바꿔 던진다.
 * {@code FinanceSyncService}가 이 scope만 FAILED로 기록하고 사용자에게 같은 메시지를 보여준다.
 */
@Component
public class AccountSyncHandler implements FinanceSyncScopeHandler {

    private static final Logger log = LoggerFactory.getLogger(AccountSyncHandler.class);
    private static final DateTimeFormatter FINANCE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String DEFAULT_CURRENCY = "KRW";

    private final SsafyFinanceClient ssafyFinanceClient;
    private final AccountRepository accountRepository;
    private final FieldCryptor fieldCryptor;
    private final SensitiveNumberHasher sensitiveNumberHasher;
    private final AccountClock accountClock;
    private final TransactionTemplate transactionTemplate;

    public AccountSyncHandler(
            SsafyFinanceClient ssafyFinanceClient,
            AccountRepository accountRepository,
            FieldCryptor fieldCryptor,
            SensitiveNumberHasher sensitiveNumberHasher,
            AccountClock accountClock,
            TransactionTemplate transactionTemplate
    ) {
        this.ssafyFinanceClient = ssafyFinanceClient;
        this.accountRepository = accountRepository;
        this.fieldCryptor = fieldCryptor;
        this.sensitiveNumberHasher = sensitiveNumberHasher;
        this.accountClock = accountClock;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public FinanceSyncScope scope() {
        return FinanceSyncScope.ACCOUNT;
    }

    @Override
    public FinanceSyncScopeResult sync(Long userId, String userKey) {
        List<FinanceAccount> financeAccounts = fetch(userId, userKey);
        Integer synced = transactionTemplate.execute(status -> apply(userId, financeAccounts));
        return FinanceSyncScopeResult.completed(scope(), Objects.requireNonNullElse(synced, 0));
    }

    /**
     * 금융망을 호출하고 응답을 해석까지 끝낸다. 해석 실패도 여기서 FINANCE_API_UNAVAILABLE로 바꿔야
     * 저장 트랜잭션 안에서 예외가 나지 않는다.
     */
    private List<FinanceAccount> fetch(Long userId, String userKey) {
        try {
            List<SsafyDemandDepositAccount> accounts = ssafyFinanceClient.inquireDemandDepositAccountList(userKey);
            if (accounts == null) {
                return List.of();
            }
            return accounts.stream()
                    .filter(account -> {
                        if (account.accountNo() == null || account.accountNo().isBlank()) {
                            log.warn("SSAFY account list contained an entry without accountNo for user {}", userId);
                            return false;
                        }
                        return true;
                    })
                    .map(account -> new FinanceAccount(account.accountNo().strip(), toSnapshot(account)))
                    .toList();
        } catch (SsafyApiException exception) {
            log.warn("SSAFY finance API error during account sync: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.FINANCE_API_ERROR);
        } catch (SsafyUnavailableException exception) {
            log.warn("SSAFY finance API unavailable during account sync: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.FINANCE_API_UNAVAILABLE);
        }
    }

    private int apply(Long userId, List<FinanceAccount> financeAccounts) {
        LocalDateTime now = accountClock.now();
        Map<String, Account> existingByHash = new HashMap<>();
        for (Account account : accountRepository.findAllByUserId(userId)) {
            existingByHash.put(account.getAccountNoHash(), account);
        }

        int changed = 0;
        Set<String> seenHashes = new HashSet<>();
        for (FinanceAccount financeAccount : financeAccounts) {
            String accountNo = financeAccount.accountNo();
            String hash = sensitiveNumberHasher.hash(accountNo);
            if (!seenHashes.add(hash)) {
                continue;
            }
            FinanceSnapshot snapshot = financeAccount.snapshot();
            Account existing = existingByHash.get(hash);
            if (existing == null) {
                accountRepository.save(Account.fromFinance(
                        userId,
                        snapshot,
                        fieldCryptor.encrypt(accountNo),
                        hash,
                        AccountNumberMasker.mask(accountNo),
                        now
                ));
                changed++;
            } else if (existing.isFromFinance()) {
                if (existing.applyFinanceSnapshot(snapshot, now)) {
                    changed++;
                }
            } else {
                log.debug("Account {} of user {} is {} sourced; finance snapshot ignored",
                        existing.getId(), userId, existing.getSourceType());
            }
        }

        for (Account account : existingByHash.values()) {
            if (account.isFromFinance()
                    && account.getAccountType() == AccountType.DEMAND_DEPOSIT
                    && !seenHashes.contains(account.getAccountNoHash())
                    && account.close(now)) {
                changed++;
            }
        }
        return changed;
    }

    /**
     * 해석이 끝난 금융망 계좌 1건. 계좌번호 원문은 암호화·해시·마스킹 입력으로만 쓰고 저장 트랜잭션 밖으로 나가지 않는다.
     */
    private record FinanceAccount(String accountNo, FinanceSnapshot snapshot) {
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

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new SsafyUnavailableException("SSAFY account list entry is missing " + field, null);
        }
        return value.strip();
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip(), FINANCE_DATE);
        } catch (DateTimeParseException exception) {
            throw new SsafyUnavailableException("SSAFY account list entry has an unparseable date: " + value, null);
        }
    }
}
