package com.ssafy.dodam.domain.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.dodam.domain.loan.service.LoanAnalysisClock;
import com.ssafy.dodam.global.crypto.FieldCryptor;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 로컬 일회용 DB에서만 실행한다. 일반 test는 외부 DB 없이 이 테스트를 건너뛴다. */
@EnabledIfEnvironmentVariable(named = "LOAN_MYSQL_TEST_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/dodam_532_test(?:\\?.*)?")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Transactional
class LoanAnalysisMySqlIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FieldCryptor cryptor;
    @Autowired private SensitiveNumberHasher hasher;
    @MockitoBean private LoanAnalysisClock clock;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("LOAN_MYSQL_TEST_URL"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    @WithMockUser(username = "532001")
    void 실제_Flyway와_암호화_fixture로_조회와_소유권을_검증한다() throws Exception {
        when(clock.today()).thenReturn(LocalDate.of(2026, 9, 9));
        // 운영 자격증명이나 데이터 없이 생성하며 각 테스트 트랜잭션에서 롤백한다.
        String ownNumber = "5320010000001";
        String otherNumber = "5320020000001";
        insertUserAndLoan(532001, ownNumber);
        insertUserAndLoan(532002, otherNumber);
        jdbc.update("INSERT INTO financial_profile (user_id, monthly_income, updated_at) VALUES (532001, 2800000, NOW())");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class))
                .isGreaterThanOrEqualTo(7);
        mvc.perform(get("/api/v1/loans/monthly-repayments").param("startMonth", "2026-10").param("endMonth", "2026-10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.months[0].remaining.totalInterest").value(8000));
        mvc.perform(get("/api/v1/loans/repayment-burden").param("yearMonth", "2026-10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.burdenRate").value(14.57));
        mvc.perform(get("/api/v1/loans/{accountNo}/interest-forecast", ownNumber))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fullTermInterest").value(24000));
        mvc.perform(get("/api/v1/loans/interest-summary").param("months", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalInterest").value(24000));
        mvc.perform(get("/api/v1/loans/cost-comparison").param("months", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.loans[0].interestShare").value(100.00));
        mvc.perform(get("/api/v1/loans/{accountNo}/interest-forecast", otherNumber))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/loans/repayment-burden"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.burdenRate", nullValue()));
    }

    private void insertUserAndLoan(long id, String number) {
        jdbc.update("INSERT INTO user (user_id, email, name, created_at, updated_at) VALUES (?, ?, 'test', NOW(), NOW())",
                id, "loan-" + id + "@example.com");
        jdbc.update("""
                INSERT INTO account (account_id, user_id, bank_code, bank_name, account_no_encrypted,
                    account_no_hash, account_no_masked, account_name, account_type, balance, created_at, updated_at)
                VALUES (?, ?, 'TEST', 'test', ?, ?, '***', 'test', 'DEMAND_DEPOSIT', 0, NOW(), NOW())
                """, id, id, cryptor.encrypt(number), hasher.hash(number));
        jdbc.update("""
                INSERT INTO loan (loan_id, user_id, withdrawal_account_id, withdrawal_account_type,
                    institution_code, institution_name, account_no_encrypted, account_no_hash, account_no_masked,
                    account_name, loan_type, repayment_type, status, original_principal, balance, interest_rate,
                    repayment_day, next_repayment_date, maturity_date, created_at)
                VALUES (?, ?, ?, 'DEMAND_DEPOSIT', 'TEST', 'test', ?, ?, '***', 'test', 'CREDIT',
                    'EQUAL_PRINCIPAL', 'ACTIVE', 1200000, 1200000, 12, 9, '2026-09-09', '2026-11-09', NOW())
                """, id, id, id, cryptor.encrypt(number), hasher.hash(number));
    }
}
