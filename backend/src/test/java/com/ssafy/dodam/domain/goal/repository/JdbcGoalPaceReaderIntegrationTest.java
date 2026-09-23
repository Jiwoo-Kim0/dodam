package com.ssafy.dodam.domain.goal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 저축속도 입출금 부분의 귀속 범위(702)와 계좌별 분리(703): 연결 계좌의 거래와 그 계좌에서 대금이 빠지는 카드의 승인만 센다.
 * 연결하지 않은 계좌·카드, 다른 사용자, 숨긴 거래는 제외한다. 실제 SQL을 H2(MySQL 모드)로 실행한다.
 */
@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:goalpace702;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class JdbcGoalPaceReaderIntegrationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 15, 12, 0);
    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 31);

    @Autowired JdbcGoalPaceReader reader;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired SensitiveNumberHasher hasher;

    private Long owner;
    private Long other;
    private Long linkedAccount;
    private Long unlinkedAccount;
    private Long otherAccount;
    private Long linkedCard;
    private Long unlinkedCard;
    private Long otherCard;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM `transaction`");
        jdbc.update("DELETE FROM card");
        jdbc.update("DELETE FROM account");
        owner = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "목표 소유자")).getId();
        other = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "다른 사용자")).getId();
        linkedAccount = insertAccount(owner, "1111222233334444");
        unlinkedAccount = insertAccount(owner, "5555666677778888");
        otherAccount = insertAccount(other, "9999000011112222");
        linkedCard = insertCard(owner, linkedAccount, "c1");
        unlinkedCard = insertCard(owner, unlinkedAccount, "c2");
        otherCard = insertCard(other, otherAccount, "c3");
    }

    @Test
    void countsLinkedAccountRowsAndApprovalsOfCardsDrawnOnIt() {
        transaction(owner, linkedAccount, null, LocalDate.of(2026, 6, 25), "INCOME", 1_000_000L, false);
        transaction(owner, linkedAccount, null, LocalDate.of(2026, 7, 5), "EXPENSE", 200_000L, false);
        // 카드 승인: 계좌 없이 카드만 있어도 대금이 연결 계좌에서 빠지므로 센다
        transaction(owner, null, linkedCard, LocalDate.of(2026, 7, 10), "EXPENSE", 300_000L, false);
        // 제외: 연결하지 않은 계좌·카드, 다른 사용자, 숨긴 거래, 창 밖
        transaction(owner, unlinkedAccount, null, LocalDate.of(2026, 7, 12), "INCOME", 5_000_000L, false);
        transaction(owner, null, unlinkedCard, LocalDate.of(2026, 7, 13), "EXPENSE", 50_000L, false);
        transaction(other, otherAccount, otherCard, LocalDate.of(2026, 7, 14), "EXPENSE", 70_000L, false);
        transaction(owner, linkedAccount, null, LocalDate.of(2026, 7, 15), "EXPENSE", 90_000L, true);
        transaction(owner, null, linkedCard, LocalDate.of(2026, 9, 2), "EXPENSE", 999_999L, false);

        assertThat(reader.readNetCashflowByAccount(owner, List.of(linkedAccount), FROM, TO))
                .containsOnly(entry(linkedAccount, 500_000L));
        assertThat(reader.readNetCashflowByAccount(other, List.of(linkedAccount), FROM, TO)).isEmpty();
    }

    @Test
    void splitsNetCashflowByAttributedAccount() {
        // 703: 목표 몫을 계좌마다 따로 곱해야 하므로 합계가 아니라 계좌별로 돌려준다.
        // 카드 승인은 계좌 열이 비어 있어도 대금이 빠지는 출금 계좌 몫으로 돌아간다.
        transaction(owner, linkedAccount, null, LocalDate.of(2026, 6, 25), "INCOME", 1_000_000L, false);
        transaction(owner, null, linkedCard, LocalDate.of(2026, 7, 10), "EXPENSE", 300_000L, false);
        transaction(owner, unlinkedAccount, null, LocalDate.of(2026, 7, 12), "INCOME", 400_000L, false);
        transaction(owner, null, unlinkedCard, LocalDate.of(2026, 7, 13), "EXPENSE", 50_000L, false);

        // 두 계좌를 모두 넘기면 둘 다 나온다(여기서는 이름과 달리 둘 다 조회 대상이다)
        assertThat(reader.readNetCashflowByAccount(owner, List.of(linkedAccount, unlinkedAccount), FROM, TO))
                .containsOnly(entry(linkedAccount, 700_000L), entry(unlinkedAccount, 350_000L));
        // 하나만 넘기면 나머지 계좌·카드의 거래는 아예 들어오지 않는다
        assertThat(reader.readNetCashflowByAccount(owner, List.of(linkedAccount), FROM, TO))
                .containsOnly(entry(linkedAccount, 700_000L));
    }

    @Test
    void firstTransactionDateComesFromLinkedAccountOrItsCardsOnly() {
        transaction(owner, unlinkedAccount, null, LocalDate.of(2026, 1, 3), "INCOME", 10_000L, false);
        transaction(owner, null, unlinkedCard, LocalDate.of(2026, 2, 3), "EXPENSE", 10_000L, false);
        transaction(owner, linkedAccount, null, LocalDate.of(2026, 4, 20), "INCOME", 10_000L, false);
        transaction(owner, null, linkedCard, LocalDate.of(2026, 3, 9), "EXPENSE", 10_000L, false);

        assertThat(reader.readFirstTransactionDate(owner, List.of(linkedAccount))).contains(LocalDate.of(2026, 3, 9));
        assertThat(reader.readFirstTransactionDate(owner, List.of(unlinkedAccount))).contains(LocalDate.of(2026, 1, 3));
        assertThat(reader.readFirstTransactionDate(owner, List.of())).isEmpty();
    }

    private void transaction(Long userId, Long accountId, Long cardId, LocalDate date, String type, long amount, boolean hidden) {
        jdbc.update("""
                INSERT INTO `transaction` (transaction_id, user_id, account_id, card_id, transaction_date, transaction_type, amount,
                    payment_method, spending_type, summary, hidden, source_type, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'VARIABLE', 'test', ?, 'SEED', ?)
                """, UUID.randomUUID().toString(), userId, accountId, cardId, date, type, amount,
                cardId == null ? "ACCOUNT" : "CARD", hidden, NOW);
    }

    private Long insertAccount(Long userId, String accountNo) {
        jdbc.update("""
                INSERT INTO account (user_id, bank_code, bank_name, account_no_encrypted, account_no_hash, account_no_masked,
                    account_name, account_type, status, balance, last_transaction_date, currency, source_type, created_at, updated_at)
                VALUES (?, '004', 'KB국민은행', X'00', ?, '1111-**-**4444', '계좌', 'DEMAND_DEPOSIT', 'ACTIVE', 0, NULL, 'KRW', 'SEED', ?, ?)
                """, userId, hasher.hash(accountNo), NOW, NOW);
        return jdbc.queryForObject("SELECT account_id FROM account WHERE user_id = ? AND account_no_hash = ?", Long.class,
                userId, hasher.hash(accountNo));
    }

    private Long insertCard(Long userId, Long accountId, String hashSeed) {
        String hash = String.format("%-64s", hashSeed).replace(' ', '0');
        jdbc.update("""
                INSERT INTO card (user_id, withdrawal_account_id, withdrawal_account_type, card_no_hash, card_no_masked,
                                  issuer_code, issuer_name, card_name, status, credit_limit, created_at)
                VALUES (?, ?, 'DEMAND_DEPOSIT', ?, '1234-****-****-5678', 'KB_CARD', 'KB국민카드', '테스트카드', 'ACTIVE', 3000000, ?)
                """, userId, accountId, hash, Timestamp.valueOf(NOW));
        return jdbc.queryForObject("SELECT card_id FROM card WHERE user_id = ? AND card_no_hash = ?", Long.class, userId, hash);
    }
}
