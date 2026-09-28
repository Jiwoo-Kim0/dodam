package com.ssafy.dodam.domain.depositsaving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ssafy.dodam.domain.depositsaving.service.DepositSavingClock;
import com.ssafy.dodam.domain.depositsaving.service.DepositSavingQueryService;
import com.ssafy.dodam.global.crypto.FieldCryptor;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 빈 일회용 dodam_535_test에서만 실행한다. 실제 운영 seed/자격증명은 사용하지 않는다.
 * seed 자체가 COMMIT하므로 테스트 트랜잭션에 의존하지 않고 전용 컨테이너 폐기로 정리한다.
 */
@EnabledIfEnvironmentVariable(
        named = "DEPOSIT_MYSQL_TEST_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/dodam_535_test(?:\\?.*)?"
)
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "logging.level.org.springframework.jdbc=INFO",
        "logging.level.org.hibernate.SQL=INFO"
})
class DepositSavingMySqlIntegrationTest {
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FieldCryptor cryptor;
    @Autowired private SensitiveNumberHasher hasher;
    @Autowired private DepositSavingQueryService service;
    @MockitoBean private DepositSavingClock clock;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DEPOSIT_MYSQL_TEST_URL"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void migrationSeedRerunOwnershipAndConstraints() throws Exception {
        when(clock.today()).thenReturn(LocalDate.of(2026, 9, 9));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account", Long.class))
                .as("전용 빈 테스트 DB만 사용한다").isZero();
        try (Connection connection = dataSource.getConnection()) {
            createPrerequisites(connection);
            var script = new EncodedResource(
                    new FileSystemResource("database/seeds/DODAM_DEPOSIT_SAVING_SEED.sql"),
                    StandardCharsets.UTF_8);
            ScriptUtils.executeSqlScript(connection, script);
            verifySeed();
            Long accountId = service.list(2L, "SAVINGS", null, 0, 20).content().getFirst().accountId();
            Long balance = service.summary(2L).totalBalance();
            ScriptUtils.executeSqlScript(connection, script);
            verifySeed();
            assertThat(service.list(2L, "SAVINGS", null, 0, 20).content().getFirst().accountId())
                    .isEqualTo(accountId);
            assertThat(service.summary(2L).totalBalance()).isEqualTo(balance);
            verifyConstraints(accountId);
        }
    }

    private void verifySeed() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deposit_saving", Long.class)).isEqualTo(4000);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM savings_payment", Long.class)).isEqualTo(13000);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM account WHERE account_type = 'DEMAND_DEPOSIT' AND balance = 1234567",
                Long.class)).isEqualTo(4000);
        assertThat(service.list(1L, "SAVINGS", null, 0, 20).totalElements()).isZero();
        var own = service.list(2L, "SAVINGS", null, 0, 20);
        assertThat(own.totalElements()).isEqualTo(1);
        Long expectedPaid = jdbc.queryForObject("""
                SELECT COALESCE(SUM(p.amount), 0) FROM savings_payment p
                JOIN deposit_saving d ON d.account_id = p.deposit_saving_id
                WHERE d.user_id = 2 AND p.status = 'PAID'
                  AND p.payment_date BETWEEN '2026-08-01' AND '2026-08-31'
                """, Long.class);
        assertThat(expectedPaid).isPositive();
        var rate = service.rate(2L, "2026-08");
        assertThat(rate.paidAmount()).isEqualTo(expectedPaid);
        assertThat(rate.rate()).isEqualByComparingTo(
                java.math.BigDecimal.valueOf(expectedPaid).divide(
                        java.math.BigDecimal.valueOf(30000), 2, java.math.RoundingMode.HALF_UP));
        assertThat(service.rate(2L, null).paidAmount()).isZero();
    }

    private void verifyConstraints(Long accountId) {
        assertCheckRejected(() -> jdbc.update(
                "UPDATE deposit_saving SET withdrawal_account_type = NULL WHERE account_id = ?", accountId));
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE deposit_saving SET user_id = 1 WHERE account_id = ?", accountId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE deposit_saving SET withdrawal_account_id = 1 WHERE account_id = ?", accountId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertCheckRejected(() -> jdbc.update("""
                UPDATE deposit_saving SET withdrawal_account_id = NULL, withdrawal_account_type = NULL
                WHERE account_id = ?
                """, accountId));
        assertCheckRejected(() -> jdbc.update(
                "UPDATE deposit_saving SET account_type = 'DEMAND_DEPOSIT' WHERE account_id = ?", accountId));
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO savings_payment (deposit_saving_id, account_type, payment_date, amount, installment_number, status)
                VALUES (?, 'SAVINGS', '2026-08-01', 1, 1, 'PAID')
                """, accountId)).isInstanceOf(DataIntegrityViolationException.class);
        assertCheckRejected(() -> jdbc.update(
                "UPDATE savings_payment SET amount = -1 WHERE deposit_saving_id = ?", accountId));
        assertCheckRejected(() -> jdbc.update(
                "UPDATE savings_payment SET status = 'UNKNOWN' WHERE deposit_saving_id = ?", accountId));
    }

    private void assertCheckRejected(Runnable mutation) {
        // MySQL CHECK 3819의 SQLState는 HY000이므로 Spring의 무결성 예외로 분류되지 않을 수 있다.
        assertThatThrownBy(mutation::run).isInstanceOf(DataAccessException.class)
                .hasRootCauseInstanceOf(SQLException.class)
                .satisfies(exception -> assertThat(((SQLException) exception.getCause()).getErrorCode())
                        .isEqualTo(3819));
    }

    private void createPrerequisites(Connection connection) throws Exception {
        // 491번 전체 데이터 대신 524번이 요구하는 사용자·입출금계좌와 동일한 자연키 계약만 준비한다.
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TEMPORARY TABLE tmp_dodam_524_account_keys
                    (user_email VARCHAR(255) PRIMARY KEY, account_no_hash CHAR(64) NOT NULL)
                    """);
            for (String type : new String[]{"deposit", "savings"}) {
                statement.execute("CREATE TEMPORARY TABLE tmp_dodam_524_" + type + "_crypto "
                        + "(seed_account_code VARCHAR(50) PRIMARY KEY, account_no_encrypted VARBINARY(512) NOT NULL,"
                        + " account_no_hash CHAR(64) NOT NULL, account_no_masked VARCHAR(100) NOT NULL)");
            }
        }
        try (PreparedStatement users = connection.prepareStatement("""
                    INSERT INTO user (user_id, email, name, created_at, updated_at)
                    VALUES (?, ?, '시연 사용자', NOW(), NOW())
                    """);
             PreparedStatement accounts = connection.prepareStatement("""
                    INSERT INTO account (account_id, user_id, bank_code, bank_name, account_no_encrypted,
                        account_no_hash, account_no_masked, account_name, account_type, balance, source_type, created_at, updated_at)
                    VALUES (?, ?, 'TEST', '시연은행', ?, ?, '***', '입출금계좌', 'DEMAND_DEPOSIT', 1234567, 'SEED', NOW(), NOW())
                    """);
             PreparedStatement keys = connection.prepareStatement(
                     "INSERT INTO tmp_dodam_524_account_keys VALUES (?, ?)");
             PreparedStatement deposits = connection.prepareStatement(
                     "INSERT INTO tmp_dodam_524_deposit_crypto VALUES (?, ?, ?, ?)");
             PreparedStatement savings = connection.prepareStatement(
                     "INSERT INTO tmp_dodam_524_savings_crypto VALUES (?, ?, ?, ?)")) {
            for (int i = 1; i <= 4000; i++) {
                String email = "seed.kmeans.u%04d@dodam.invalid".formatted(i);
                String demandNumber = "535100%07d".formatted(i);
                String productNumber = "535200%07d".formatted(i);
                users.setLong(1, i);
                users.setString(2, email);
                users.addBatch();
                accounts.setLong(1, i);
                accounts.setLong(2, i);
                accounts.setBytes(3, cryptor.encrypt(demandNumber));
                accounts.setString(4, hasher.hash(demandNumber));
                accounts.addBatch();
                keys.setString(1, email);
                keys.setString(2, hasher.hash(demandNumber));
                keys.addBatch();
                PreparedStatement crypto = i % 2 == 1 ? deposits : savings;
                crypto.setString(1, "U%04d_%s".formatted(i, i % 2 == 1 ? "DEPOSIT" : "SAVINGS"));
                crypto.setBytes(2, cryptor.encrypt(productNumber));
                crypto.setString(3, hasher.hash(productNumber));
                // 실제 계좌번호를 사용하지 않는 테스트용 비식별 표시다.
                crypto.setString(4, "***-" + productNumber.substring(productNumber.length() - 2));
                crypto.addBatch();
            }
            users.executeBatch();
            accounts.executeBatch();
            keys.executeBatch();
            deposits.executeBatch();
            savings.executeBatch();
        }
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO financial_profile (user_id, monthly_income, updated_at)
                    VALUES (2, 3000000, NOW())
                    """);
        }
    }
}
