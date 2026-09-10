package com.ssafy.dodam.domain.minusaccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.dodam.global.crypto.FieldCryptor;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 일회용 MySQL에서만 실행한다. 일반 test는 외부 DB 없이 이 테스트를 건너뛴다.
 *
 * <p>H2 테스트는 Flyway를 끄고 엔티티로 스키마를 만들기 때문에 `V14`의 DDL과 엔티티 매핑이
 * 어긋나도 잡지 못한다(529에서 `card`의 CHAR 컬럼이 실제로 그렇게 실패했다). 이 테스트는 실제
 * Flyway 마이그레이션 위에서 `ddl-auto: validate`를 통과시키고, MySQL CHECK가 살아 있는 상태로
 * 상환 경로가 금액 정합을 지키는지 확인한다.
 *
 * <p>실행 방법: 빈 `dodam_534_test` 스키마를 가진 MySQL 8.0 컨테이너를 띄우고
 * `MINUS_MYSQL_TEST_URL=jdbc:mysql://127.0.0.1:<port>/dodam_534_test`를 설정한다.
 */
@EnabledIfEnvironmentVariable(
        named = "MINUS_MYSQL_TEST_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/dodam_534_test(?:\\?.*)?"
)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Transactional
class MinusAccountMySqlIntegrationTest {

    private static final String BASE = "/api/v1/loans/minus-accounts";
    private static final String OWN_MINUS_NO = "5340010000001";
    private static final String OWN_WITHDRAWAL_NO = "5340010000002";
    private static final String OTHER_MINUS_NO = "5340020000001";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FieldCryptor cryptor;

    @Autowired
    private SensitiveNumberHasher hasher;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("MINUS_MYSQL_TEST_URL"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    @WithMockUser(username = "534001")
    void 실제_Flyway_스키마에서_조회와_상환_정합을_검증한다() throws Exception {
        // 운영 자격증명이나 데이터 없이 생성하며 각 테스트 트랜잭션에서 롤백한다.
        insertUser(534001, OWN_MINUS_NO, OWN_WITHDRAWAL_NO, 10000000, 3500000, 21000);
        insertUser(534002, OTHER_MINUS_NO, null, 20000000, 19400000, 148000);

        // V14가 실제로 적용됐는지 확인한다.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND version = '14'",
                Integer.class
        )).isEqualTo(1);

        // ERD의 CHECK 7개가 MySQL에 만들어졌는지 확인한다.
        assertThat(jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS
                 WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME LIKE 'chk_minus_account%'
                """,
                Integer.class
        )).isEqualTo(7);

        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].limitAmount").value(10000000))
                .andExpect(jsonPath("$.data[0].usageRate").value(0.3500));

        mvc.perform(get(BASE + "/" + OWN_MINUS_NO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(-3500000));

        mvc.perform(get(BASE + "/" + OWN_MINUS_NO + "/interest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accruedInterest").value(21000))
                .andExpect(jsonPath("$.data.estimatedDailyInterest").value(690));

        // 타인 계좌는 존재 여부를 노출하지 않는다.
        mvc.perform(get(BASE + "/" + OTHER_MINUS_NO))
                .andExpect(status().isNotFound());

        // CHECK가 살아 있는 상태에서 상환한다. 금액 정합이 깨지면 여기서 제약 위반으로 실패한다.
        mvc.perform(post(BASE + "/" + OWN_MINUS_NO + "/repayments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedAmount").value(3000000))
                .andExpect(jsonPath("$.data.availableAmount").value(7000000));

        assertThat(jdbc.queryForObject(
                "SELECT balance FROM account WHERE account_id = 534001", Long.class
        )).isEqualTo(-3000000L);
        assertThat(jdbc.queryForObject(
                "SELECT used_amount + available_amount FROM minus_account WHERE account_id = 534001",
                Long.class
        )).isEqualTo(10000000L);

        mvc.perform(post(BASE + "/" + OWN_MINUS_NO + "/interest-payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accruedInterest").value(0))
                // 이자 납부는 원금을 건드리지 않는다.
                .andExpect(jsonPath("$.data.usedAmount").value(3000000));
    }

    private void insertUser(
            long id,
            String minusNumber,
            String withdrawalNumber,
            long limitAmount,
            long usedAmount,
            long accruedInterest
    ) {
        jdbc.update(
                "INSERT INTO user (user_id, email, name, created_at, updated_at)"
                        + " VALUES (?, ?, 'test', NOW(), NOW())",
                id, "minus-" + id + "@example.com"
        );
        jdbc.update(
                """
                INSERT INTO account (account_id, user_id, bank_code, bank_name, account_no_encrypted,
                    account_no_hash, account_no_masked, account_name, account_type, status, balance,
                    created_at, updated_at)
                VALUES (?, ?, 'TEST', 'test', ?, ?, '***', 'test', 'MINUS', 'ACTIVE', ?, NOW(), NOW())
                """,
                id, id, cryptor.encrypt(minusNumber), hasher.hash(minusNumber), -usedAmount
        );
        jdbc.update(
                """
                INSERT INTO minus_account (account_id, user_id, account_type, limit_amount, used_amount,
                    available_amount, interest_rate, accrued_interest, next_interest_date, maturity_date,
                    overdue_days, created_at)
                VALUES (?, ?, 'MINUS', ?, ?, ?, 7.2000, ?, NULL, '2027-06-30', 0, NOW())
                """,
                id, id, limitAmount, usedAmount, limitAmount - usedAmount, accruedInterest
        );

        if (withdrawalNumber != null) {
            jdbc.update(
                    """
                    INSERT INTO account (account_id, user_id, bank_code, bank_name, account_no_encrypted,
                        account_no_hash, account_no_masked, account_name, account_type, status, balance,
                        created_at, updated_at)
                    VALUES (?, ?, 'TEST', 'test', ?, ?, '***', 'test', 'DEMAND_DEPOSIT', 'ACTIVE',
                        5000000, NOW(), NOW())
                    """,
                    id + 900000, id, cryptor.encrypt(withdrawalNumber), hasher.hash(withdrawalNumber)
            );
        }
    }
}
