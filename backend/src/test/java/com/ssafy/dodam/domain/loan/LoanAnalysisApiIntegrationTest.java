package com.ssafy.dodam.domain.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.dodam.domain.loan.service.LoanAnalysisClock;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:loan_analysis;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@Sql(scripts = {"/sql/loan-query-test-data.sql", "/sql/loan-analysis-test-data.sql"})
class LoanAnalysisApiIntegrationTest {
    private static final String ACCOUNT = "1101110001111";
    private static final String OTHER = "1103330003333";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SensitiveNumberHasher hasher;
    @MockitoBean private LoanAnalysisClock clock;

    @BeforeEach
    void setUp() {
        when(clock.today()).thenReturn(LocalDate.of(2026, 9, 9));
        jdbc.update("UPDATE loan SET account_no_hash = ? WHERE loan_id = 1", hasher.hash(ACCOUNT));
        jdbc.update("UPDATE loan SET account_no_hash = ? WHERE loan_id = 3", hasher.hash(OTHER));
    }

    @Test
    @WithMockUser(username = "1")
    void 다섯_API가_본인_데이터만_같은_계산으로_제공한다() throws Exception {
        mvc.perform(get("/api/v1/loans/monthly-repayments").param("startMonth", "2026-09").param("endMonth", "2026-11"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.months", hasSize(3)))
                .andExpect(jsonPath("$.data.months[0].remaining.totalInterest").value(24000))
                .andExpect(jsonPath("$.data.months[0].remaining.remainingRepaymentAmount").value(424000))
                .andExpect(jsonPath("$.data.months[0].monthlyTotal.monthlyRepaymentAmount", nullValue()));
        mvc.perform(get("/api/v1/loans/repayment-burden").param("yearMonth", "2026-10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.burdenRate").value(15.00))
                .andExpect(jsonPath("$.data.monthlyTotal.monthlyRepaymentAmount").value(420000))
                .andExpect(jsonPath("$.data.unavailableLoans", hasSize(0)));
        mvc.perform(get("/api/v1/loans/{accountNo}/interest-forecast", ACCOUNT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fullTermInterest").value(24000))
                .andExpect(jsonPath("$.data.status").value("COMPLETE"));
        mvc.perform(get("/api/v1/loans/interest-summary").param("months", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalInterest").value(60000));
        String content = mvc.perform(get("/api/v1/loans/cost-comparison").param("months", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalInterest").value(60000))
                .andExpect(jsonPath("$.data.loans[0].loanId").value(2))
                .andExpect(jsonPath("$.data.loans[0].interestShare").value(60.00))
                .andReturn().getResponse().getContentAsString();
        assertThat(content).doesNotContain(ACCOUNT, OTHER, hasher.hash(ACCOUNT), "accountNoHash", "accountNoEncrypted");
    }

    @ParameterizedTest
    @ValueSource(strings = {"monthly-repayments", "repayment-burden", "interest-summary", "cost-comparison", "1101110001111/interest-forecast"})
    void 다섯_API는_인증이_필수다(String path) throws Exception {
        mvc.perform(get("/api/v1/loans/" + path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {OTHER, "9999999999999"})
    @WithMockUser(username = "1")
    void 타인과_미존재_대출을_구분하지_않는다(String account) throws Exception {
        mvc.perform(get("/api/v1/loans/{accountNo}/interest-forecast", account))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456789", "123456789012345678901", "abc123456789"})
    @WithMockUser(username = "1")
    void 계좌번호_형식을_검증하고_오류에_원문을_싣지_않는다(String account) throws Exception {
        String content = mvc.perform(get("/api/v1/loans/{accountNo}/interest-forecast", account))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andReturn().getResponse().getContentAsString();
        assertThat(content).doesNotContain(account);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890", "12345678901234567890"})
    @WithMockUser(username = "1")
    void 계좌번호_허용길이는_소유권조회까지_진행한다(String account) throws Exception {
        mvc.perform(get("/api/v1/loans/{accountNo}/interest-forecast", account)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "2026-9", "2026-13", "0000-01", "2026-08", "2027-09", "9999-12"})
    @WithMockUser(username = "1")
    void 연월_입력오류는_400이다(String month) throws Exception {
        mvc.perform(get("/api/v1/loans/repayment-burden").param("yearMonth", month))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/loans/monthly-repayments").param("startMonth", month))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({"0,VALIDATION_ERROR", "13,VALIDATION_ERROR", "-1,VALIDATION_ERROR", "1.5,INVALID_REQUEST", "x,INVALID_REQUEST", "2147483648,INVALID_REQUEST"})
    @WithMockUser(username = "1")
    void 개월수_검증은_두_API에_동일하게_적용한다(String months, String code) throws Exception {
        for (String endpoint : new String[]{"interest-summary", "cost-comparison"}) {
            mvc.perform(get("/api/v1/loans/" + endpoint).param("months", months))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @WithMockUser(username = "1")
    void 빈_개월수에는_기본값을_적용하지_않는다(String months) throws Exception {
        for (String endpoint : new String[]{"interest-summary", "cost-comparison"}) {
            mvc.perform(get("/api/v1/loans/" + endpoint).param("months", months))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
    }

    @Test
    @WithMockUser(username = "1")
    void 기본기간과_한쪽생략과_상한을_검증한다() throws Exception {
        mvc.perform(get("/api/v1/loans/monthly-repayments"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.months", hasSize(6)));
        mvc.perform(get("/api/v1/loans/monthly-repayments").param("startMonth", "2026-10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.endMonth").value("2027-03"));
        mvc.perform(get("/api/v1/loans/monthly-repayments").param("endMonth", "2026-10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.months", hasSize(2)));
        mvc.perform(get("/api/v1/loans/interest-summary"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.months", hasSize(12)));
        mvc.perform(get("/api/v1/loans/interest-summary").param("months", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.months", hasSize(1)));
        mvc.perform(get("/api/v1/loans/monthly-repayments").param("startMonth", "2026-11").param("endMonth", "2026-10"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/loans/monthly-repayments").param("endMonth", "2027-08"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.months", hasSize(12)));
    }

    @Test
    @WithMockUser(username = "99")
    void 빈_사용자는_0원과_빈목록을_반환하고_소득을_생성하지_않는다() throws Exception {
        mvc.perform(get("/api/v1/loans/cost-comparison"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.complete").value(true))
                .andExpect(jsonPath("$.data.totalInterest").value(0)).andExpect(jsonPath("$.data.loans", hasSize(0)));
        mvc.perform(get("/api/v1/loans/repayment-burden"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.monthlyTotal.monthlyRepaymentAmount").value(0))
                .andExpect(jsonPath("$.data.monthlyIncome", nullValue()))
                .andExpect(jsonPath("$.data.unavailableReasons", hasItem("INCOME_MISSING")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_profile WHERE user_id = 99", Long.class)).isZero();
    }

    @Test
    @WithMockUser(username = "1")
    void 현재월_부담률은_납부전후에_근거없이_0이_되지_않는다() throws Exception {
        for (LocalDate date : new LocalDate[]{LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10)}) {
            when(clock.today()).thenReturn(date);
            mvc.perform(get("/api/v1/loans/repayment-burden"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.burdenRate", nullValue()))
                    .andExpect(jsonPath("$.data.unavailableReasons", hasItem("MONTH_TOTAL_UNAVAILABLE")));
        }
    }

    @Test
    @WithMockUser(username = "1")
    void 계산불가_대출은_부분합과_사유를_제공한다() throws Exception {
        jdbc.update("UPDATE loan SET maturity_date = NULL WHERE loan_id = 2");
        mvc.perform(get("/api/v1/loans/interest-summary").param("months", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.complete").value(false))
                .andExpect(jsonPath("$.data.knownInterest").value(24000))
                .andExpect(jsonPath("$.data.totalInterest", nullValue()))
                .andExpect(jsonPath("$.data.unavailableLoans[0].loanId").value(2));
    }

    @Test
    @WithMockUser(username = "1")
    void 개별기간합계_초과도_월별_정상결과를_보존한다() throws Exception {
        jdbc.update("UPDATE loan SET original_principal = ?, balance = ?, interest_rate = 12, repayment_type = 'BULLET', maturity_date = ? WHERE loan_id = 1",
                9000000000000000000L, 9000000000000000000L, LocalDate.of(2036, 8, 9));
        mvc.perform(get("/api/v1/loans/{accountNo}/interest-forecast", ACCOUNT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PARTIAL"))
                .andExpect(jsonPath("$.data.reasons", hasItem("AGGREGATE_AMOUNT_OUT_OF_RANGE")))
                .andExpect(jsonPath("$.data.truncated").value(false))
                .andExpect(jsonPath("$.data.knownInterest", nullValue()))
                .andExpect(jsonPath("$.data.coveredThrough").value("2036-08-09"))
                .andExpect(jsonPath("$.data.months", hasSize(120)));
    }
}
