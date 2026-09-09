package com.ssafy.dodam.domain.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.dodam.domain.loan.service.LoanAnalysisClock;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

/**
 * 상환 우선순위 API 검증.
 *
 * <p>fixture의 사용자 1은 상환 중 대출 3건(loan 1·2·5), 잔액 0 완료 대출 1건(loan 4)을 가지고 사용자 2는
 * loan 3만 가진다. 순위 대상은 상환 중이면서 잔액이 양수인 3건이다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:loan_repayment_priority;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@Sql(scripts = "/sql/loan-query-test-data.sql")
class LoanRepaymentPriorityApiIntegrationTest {
    private static final String PATH = "/api/v1/loans/repayment-priority";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private LoanAnalysisClock clock;

    @BeforeEach
    void setUp() {
        when(clock.today()).thenReturn(LocalDate.of(2026, 9, 9));
    }

    @Test
    @WithMockUser(username = "1")
    void 금리_내림차순으로_본인의_상환중_대출만_순위를_매긴다() throws Exception {
        String content = mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.asOfDate").value("2026-09-09"))
                .andExpect(jsonPath("$.data.strategy").value("HIGHEST_RATE_FIRST"))
                .andExpect(jsonPath("$.data.complete").value(true))
                .andExpect(jsonPath("$.data.evaluatedCount").value(3))
                .andExpect(jsonPath("$.data.unavailableLoans", hasSize(0)))
                .andExpect(jsonPath("$.data.unavailableReasons", hasSize(0)))
                // 잔액 0인 완료 대출(loan 4)과 타인 대출(loan 3)은 제외된다.
                .andExpect(jsonPath("$.data.loans[*].loanId", contains(5, 1, 2)))
                .andExpect(jsonPath("$.data.loans[*].rank", contains(1, 2, 3)))
                .andExpect(jsonPath("$.data.loans[0].interestRate").value(9.9000))
                .andExpect(jsonPath("$.data.loans[0].requiresAttention").value(true))
                .andExpect(jsonPath("$.data.loans[0].status").value("COMPLETE"))
                .andExpect(jsonPath("$.data.loans[0].reasons", contains("HIGHER_CURRENT_RATE")))
                .andExpect(jsonPath("$.data.loans[0].institutionName").value("NH농협은행"))
                .andExpect(jsonPath("$.data.loans[0].balance").value(1500000))
                .andExpect(jsonPath("$.data.loans[1].requiresAttention").value(false))
                .andExpect(jsonPath("$.data.loans[2].interestRate").value(3.2000))
                // 연체 대출이 있으므로 연체 가산금리 제외 가정이 함께 제공된다.
                .andExpect(jsonPath("$.data.assumptions", contains("EXCLUDES_FEES", "EXCLUDES_GOAL_BUDGET",
                        "EXCLUDES_OVERDUE_INTEREST_AND_ARREARS", "ORDER_BY_CURRENT_RATE_ONLY")))
                .andReturn().getResponse().getContentAsString();
        assertThat(content).doesNotContain("accountNoHash", "accountNoEncrypted", "1".repeat(64));
        assertThat(content).contains("110-***-****55");
    }

    @Test
    @WithMockUser(username = "1")
    void 금리가_같으면_잔액_오름차순과_ID로_순위를_결정한다() throws Exception {
        // loan 1(잔액 8,000,000)과 loan 2(잔액 50,000,000)의 금리를 같게 만들어 동률 정렬을 확인한다.
        jdbc.update("UPDATE loan SET interest_rate = 5.4000 WHERE loan_id = 2");
        mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loans[*].loanId", contains(5, 1, 2)))
                .andExpect(jsonPath("$.data.loans[*].rank", contains(1, 2, 3)));
    }

    @Test
    @WithMockUser(username = "1")
    void 무이자_대출도_정상_값으로_순위에_포함한다() throws Exception {
        jdbc.update("UPDATE loan SET interest_rate = 0.0000 WHERE loan_id = 5");
        mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluatedCount").value(3))
                .andExpect(jsonPath("$.data.complete").value(true))
                .andExpect(jsonPath("$.data.loans[*].loanId", contains(1, 2, 5)))
                .andExpect(jsonPath("$.data.unavailableLoans", hasSize(0)));
    }

    // 금리 무효(`INVALID_TERMS`)로 순위에서 분리되는 경로는 검증하지 않는다. ERD의
    // chk_loan_interest_rate와 동일한 CHECK가 이 fixture(loan-query-test-data.sql)에도 있어
    // NOT NULL·0 이상이 강제되므로 API를 통해 도달할 수 없다. 서비스의 해당 분기는 제약이 다른 환경을
    // 대비한 방어 코드로만 남긴다.

    @Test
    @WithMockUser(username = "2")
    void 연체_대출이_없으면_연체_가정을_제공하지_않는다() throws Exception {
        mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluatedCount").value(1))
                .andExpect(jsonPath("$.data.loans[*].loanId", contains(3)))
                .andExpect(jsonPath("$.data.assumptions", contains("EXCLUDES_FEES", "EXCLUDES_GOAL_BUDGET",
                        "ORDER_BY_CURRENT_RATE_ONLY")));
    }

    @Test
    @WithMockUser(username = "999")
    void 대상_대출이_없으면_빈_배열과_complete_true다() throws Exception {
        mvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complete").value(true))
                .andExpect(jsonPath("$.data.evaluatedCount").value(0))
                .andExpect(jsonPath("$.data.loans", hasSize(0)))
                .andExpect(jsonPath("$.data.unavailableLoans", hasSize(0)));
    }

    /**
     * goalId는 Goal 스키마와 도메인이 없어 지원하지 않는다. 값의 형식을 해석하지 않고 파라미터가 있으면
     * 400으로 응답하므로, 존재 여부를 조회한 것처럼 404가 되지 않는다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"1", "0", "-1", "abc", "", "99999999999999999999"})
    @WithMockUser(username = "1")
    void goalId는_형식과_무관하게_미지원_400이다(String goalId) throws Exception {
        mvc.perform(get(PATH).param("goalId", goalId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 인증이_필수다() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }
}
