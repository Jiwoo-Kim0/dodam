package com.ssafy.dodam.domain.goal.service;

import com.ssafy.dodam.domain.account.service.AccountClock;
import com.ssafy.dodam.domain.account.service.AccountNumberMasker;
import com.ssafy.dodam.domain.finance.entity.FinanceConnection;
import com.ssafy.dodam.domain.finance.repository.FinanceConnectionRepository;
import com.ssafy.dodam.domain.finance.service.FinanceConnectionService;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.global.crypto.FieldCryptor;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyAccountBalance;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyCreatedDemandDepositAccount;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyDemandDepositAccount;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyDemandDepositProduct;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyFinanceClient;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 시연 계정이 "금융망 연결 + 도담 전용 계좌 + 금융망에 있는 주거래"인 상태로 기동되게 한다(S15P21D105-705).
 *
 * <p>seed 사용자는 finance_connection이 없어 가입 시 자동 개설이 일어나지 않고, 주거래는 seed 계좌번호라 금융망 이체가
 * 불가능하다. 무대에서 주거래 → 도담 계좌 실제 이체를 보이려면 두 계좌가 모두 금융망에 있어야 하므로 기동 직후 한 번 맞춘다.
 * 이미 맞춰진 단계는 건너뛰어 재기동해도 금융망 계좌가 늘지 않는다. 실패는 로그만 남기고 기동을 막지 않는다.
 *
 * <p>주거래 대응(goal-transfer-plan.md §5 B안): 금융망 계좌를 새로 열어 현재 도담 DB 주거래 잔액만큼 입금하고, 주거래 행의
 * 계좌번호·은행만 그 계좌로 바꾼다. account_id·잔액·거래 이력·source_type(SEED)은 유지하며, SEED라 동기화가 덮지 않는다.
 * 주거래 계좌번호가 이미 사용자의 금융망 계좌 목록에 있으면 대응이 끝난 것으로 본다.
 */
@Component
public class SafeboxDemoBootstrap {
    private static final Logger log = LoggerFactory.getLogger(SafeboxDemoBootstrap.class);
    private static final String PRIMARY_ACCOUNT_NAME = "주거래 입출금";

    private final String bootstrapEmails;
    private final UserRepository users;
    private final FinanceConnectionRepository connectionRows;
    private final FinanceConnectionService connections;
    private final SafeboxAccountService safeboxAccounts;
    private final SsafyFinanceClient finance;
    private final FieldCryptor cryptor;
    private final SensitiveNumberHasher hasher;
    private final AccountClock clock;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public SafeboxDemoBootstrap(@Value("${app.demo.bootstrap-user-emails:}") String bootstrapEmails,
            UserRepository users, FinanceConnectionRepository connectionRows, FinanceConnectionService connections,
            SafeboxAccountService safeboxAccounts, SsafyFinanceClient finance, FieldCryptor cryptor,
            SensitiveNumberHasher hasher, AccountClock clock, JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.bootstrapEmails = bootstrapEmails;
        this.users = users;
        this.connectionRows = connectionRows;
        this.connections = connections;
        this.safeboxAccounts = safeboxAccounts;
        this.finance = finance;
        this.cryptor = cryptor;
        this.hasher = hasher;
        this.clock = clock;
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Arrays.stream(bootstrapEmails.split(","))
                .map(String::strip)
                .filter(email -> !email.isEmpty())
                .forEach(email -> {
                    try {
                        prepare(email);
                    } catch (RuntimeException exception) {
                        log.warn("Demo safebox bootstrap failed for masked email {}: {}",
                                FieldCryptor.maskEmail(email), exception.toString());
                    }
                });
    }

    /** 한 사용자를 준비한다. 사용자가 없으면 아무것도 하지 않는다. */
    public void prepare(String email) {
        Optional<User> user = users.findByEmailAndDeletedAtIsNull(email);
        if (user.isEmpty()) {
            log.info("Demo safebox bootstrap skipped: no user for masked email {}", FieldCryptor.maskEmail(email));
            return;
        }
        Long userId = user.get().getId();
        boolean connected = connectionRows.findByUserId(userId).map(FinanceConnection::isConnected).orElse(false);
        if (!connected) {
            connections.connect(userId);
        }
        safeboxAccounts.open(userId);
        alignPrimaryAccount(userId);
        log.info("Demo safebox bootstrap ready for user {}", userId);
    }

    private void alignPrimaryAccount(Long userId) {
        List<PrimaryRow> rows = jdbc.query("""
                SELECT account_id, account_no_encrypted, balance FROM account
                WHERE user_id = ? AND account_type = 'DEMAND_DEPOSIT' AND status = 'ACTIVE'
                  AND source_type = 'SEED' AND account_name = ?
                """, (rs, i) -> new PrimaryRow(rs.getLong(1), rs.getBytes(2), rs.getLong(3)),
                userId, PRIMARY_ACCOUNT_NAME);
        if (rows.size() != 1 || rows.get(0).balance() <= 0) {
            log.info("Demo primary account alignment skipped for user {}: {} candidate(s)", userId, rows.size());
            return;
        }
        PrimaryRow primary = rows.get(0);
        String userKey = connections.userKeyOf(connections.requireConnected(userId));
        String currentNo = cryptor.decrypt(primary.accountNoEncrypted());
        List<SsafyDemandDepositAccount> financeAccounts =
                Objects.requireNonNullElse(finance.inquireDemandDepositAccountList(userKey), List.of());
        if (financeAccounts.stream().anyMatch(item -> currentNo.equals(item.accountNo()))) {
            return;
        }

        String safeboxBank = jdbc.query("""
                SELECT a.bank_code FROM safebox_account s JOIN account a ON a.account_id = s.account_id
                WHERE s.user_id = ?
                """, (rs, i) -> rs.getString(1), userId).stream().findFirst().orElse("");
        // 화면에서 주거래와 도담 계좌 은행이 겹치지 않게 다른 은행 상품을 우선한다.
        SsafyDemandDepositProduct product = Objects.requireNonNullElse(finance.inquireDemandDepositList(),
                        List.<SsafyDemandDepositProduct>of()).stream()
                .filter(item -> item.accountTypeUniqueNo() != null && !item.accountTypeUniqueNo().isBlank())
                .min(Comparator.comparing((SsafyDemandDepositProduct item) -> safeboxBank.equals(item.bankCode()))
                        .thenComparing(SsafyDemandDepositProduct::accountTypeUniqueNo))
                .orElseThrow(() -> new IllegalStateException("no demand deposit product"));

        SsafyCreatedDemandDepositAccount created = finance.createDemandDepositAccount(userKey,
                product.accountTypeUniqueNo());
        String accountNo = created == null || created.accountNo() == null ? "" : created.accountNo().strip();
        if (!accountNo.matches("[0-9]{10,20}")) {
            throw new IllegalStateException("created account number missing");
        }
        finance.depositToAccount(userKey, accountNo, primary.balance(), "도담 시연 준비");
        SsafyAccountBalance balance = finance.inquireAccountBalance(userKey, accountNo);
        if (balance == null || !Objects.equals(balance.accountBalance(), primary.balance())) {
            throw new IllegalStateException("deposited balance mismatch");
        }
        String bankCode = created.bankCode() != null && !created.bankCode().isBlank()
                ? created.bankCode().strip() : product.bankCode();
        String bankName = product.bankName() != null && !product.bankName().isBlank() ? product.bankName() : bankCode;
        Integer updated = transactions.execute(status -> jdbc.update("""
                UPDATE account SET account_no_encrypted = ?, account_no_hash = ?, account_no_masked = ?,
                       bank_code = ?, bank_name = ?, updated_at = ?
                WHERE account_id = ? AND user_id = ? AND balance = ?
                """, cryptor.encrypt(accountNo), hasher.hash(accountNo), AccountNumberMasker.mask(accountNo),
                bankCode, bankName, clock.now(), primary.accountId(), userId, primary.balance()));
        log.info("Demo primary account aligned for user {} to {} (rows {})", userId,
                AccountNumberMasker.mask(accountNo), updated);
    }

    private record PrimaryRow(long accountId, byte[] accountNoEncrypted, long balance) {
    }
}
