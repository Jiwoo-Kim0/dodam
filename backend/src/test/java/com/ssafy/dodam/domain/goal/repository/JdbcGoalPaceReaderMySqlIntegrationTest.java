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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 저축속도 계좌별 순현금흐름(703)을 MySQL 8.0에서 실측한다. H2(MySQL 모드)는 같은 SQL을 다르게 해석할 수 있어
 * {@link JdbcGoalPaceReaderIntegrationTest}만으로는 덮이지 않는다.
 *
 * <p>확인 대상은 파생 테이블 + 상관 서브쿼리 귀속 + {@code GROUP BY}다. 같은 {@code CASE} 식을 SELECT와 GROUP BY에
 * 두 번 쓰면 {@code :accountIds}가 서로 다른 파라미터로 펼쳐져 H2가 거부했는데, 그 회피가 MySQL에서도 같은 값을 내는지 본다.
 *
 * <p>실행 방법: 빈 {@code dodam_703_test} 스키마의 MySQL 8.0을 띄우고
 * {@code GOAL_PACE_MYSQL_TEST_URL=jdbc:mysql://127.0.0.1:<port>/dodam_703_test}를 설정한다. 마이그레이션은 버전 순서대로는
 * 적용되지 않는다 — V6(transaction)이 V11(card)을 참조하므로 <b>V6을 V11 뒤로 옮겨</b> 미리 올려 두고 Flyway는 끈다.
 * 실제 운영 seed·자격증명은 쓰지 않으며 정리는 전용 컨테이너 폐기로 한다.
 */
@EnabledIfEnvironmentVariable(
        named = "GOAL_PACE_MYSQL_TEST_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/dodam_703_test(?:\\?.*)?"
)
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "logging.level.org.springframework.jdbc=INFO",
        "logging.level.org.hibernate.SQL=INFO"
})
class JdbcGoalPaceReaderMySqlIntegrationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 15, 12, 0);
    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 31);

    @Autowired JdbcGoalPaceReader reader;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired SensitiveNumberHasher hasher;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("GOAL_PACE_MYSQL_TEST_URL"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "");
        // 스키마는 컨테이너 준비 단계에서 올린다. V6(transaction)이 V11(card)을 참조해 MySQL에서는 버전 순서대로 적용할 수 없다.
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void splitsNetCashflowByAttributedAccountOnMySql() {
        jdbc.update("DELETE FROM `transaction`");
        jdbc.update("DELETE FROM card");
        jdbc.update("DELETE FROM account");
        Long owner = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "목표 소유자")).getId();
        Long other = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "다른 사용자")).getId();
        Long first = insertAccount(owner, "1111222233334444");
        Long second = insertAccount(owner, "5555666677778888");
        Long foreign = insertAccount(other, "9999000011112222");
        Long firstCard = insertCard(owner, first, "c1");
        Long secondCard = insertCard(owner, second, "c2");
        Long foreignCard = insertCard(other, foreign, "c3");

        transaction(owner, first, null, LocalDate.of(2026, 6, 25), "INCOME", 1_000_000L, false);
        // 카드 승인은 계좌 열이 비어 있어도 대금이 빠지는 출금 계좌 몫으로 돌아간다
        transaction(owner, null, firstCard, LocalDate.of(2026, 7, 10), "EXPENSE", 300_000L, false);
        transaction(owner, second, null, LocalDate.of(2026, 7, 12), "INCOME", 400_000L, false);
        transaction(owner, null, secondCard, LocalDate.of(2026, 7, 13), "EXPENSE", 50_000L, false);
        // 제외: 다른 사용자, 숨긴 거래, 창 밖
        transaction(other, foreign, foreignCard, LocalDate.of(2026, 7, 14), "EXPENSE", 70_000L, false);
        transaction(owner, first, null, LocalDate.of(2026, 7, 15), "EXPENSE", 90_000L, true);
        transaction(owner, null, firstCard, LocalDate.of(2026, 9, 2), "EXPENSE", 999_999L, false);

        assertThat(reader.readNetCashflowByAccount(owner, List.of(first, second), FROM, TO))
                .containsOnly(entry(first, 700_000L), entry(second, 350_000L));
        // 조회 대상에서 빠진 계좌의 거래·카드는 들어오지 않는다
        assertThat(reader.readNetCashflowByAccount(owner, List.of(first), FROM, TO))
                .containsOnly(entry(first, 700_000L));
        assertThat(reader.readNetCashflowByAccount(other, List.of(first), FROM, TO)).isEmpty();
        assertThat(reader.readFirstTransactionDate(owner, List.of(first))).contains(LocalDate.of(2026, 6, 25));
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
                INSERT INTO card (user_id, withdrawal_account_id, withdrawal_account_type, card_no_encrypted, card_no_hash,
                                  card_no_masked, issuer_code, issuer_name, card_name, status, credit_limit, created_at)
                VALUES (?, ?, 'DEMAND_DEPOSIT', X'00', ?, '1234-****-****-5678', 'KB_CARD', 'KB국민카드', '테스트카드',
                        'ACTIVE', 3000000, ?)
                """, userId, accountId, hash, Timestamp.valueOf(NOW));
        return jdbc.queryForObject("SELECT card_id FROM card WHERE user_id = ? AND card_no_hash = ?", Long.class, userId, hash);
    }
}
