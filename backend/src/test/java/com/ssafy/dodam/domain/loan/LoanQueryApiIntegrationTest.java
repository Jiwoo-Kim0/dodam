package com.ssafy.dodam.domain.loan;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = "/sql/loan-query-test-data.sql", executionPhase = BEFORE_TEST_CLASS)
class LoanQueryApiIntegrationTest {

    private static final String CREDIT_LOAN_NO = "1101110001111";
    private static final String JEONSE_LOAN_NO = "1102220002222";
    private static final String OTHER_USER_LOAN_NO = "1103330003333";
    private static final String COMPLETED_LOAN_NO = "1104440004444";
    private static final String UNKNOWN_LOAN_NO = "9999999999999";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SensitiveNumberHasher sensitiveNumberHasher;

    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        updateLoanHash(1L, CREDIT_LOAN_NO);
        updateLoanHash(2L, JEONSE_LOAN_NO);
        updateLoanHash(3L, OTHER_USER_LOAN_NO);
        updateLoanHash(4L, COMPLETED_LOAN_NO);

        // 기준일에 의존하지 않도록 상환 일정과 만기를 실행일 기준으로 고정한다.
        // 1번 대출은 다음 달부터 12회차, 2번 대출은 다음 달부터 6회차가 남는다.
        updateLoanSchedule(1L, today.plusMonths(1), today.plusMonths(12));
        updateLoanSchedule(2L, today.plusMonths(1), today.plusMonths(6));

        jdbcTemplate.update("DELETE FROM loan_repayment");
        insertRepayment(1L, today.minusMonths(1), 350000, 300000, 50000, "PAID", null);
        insertRepayment(1L, today.minusMonths(2), 350000, 298000, 52000, "PAID", null);
        insertRepayment(1L, today.minusMonths(5), 350000, 290000, 60000, "FAILED", "잔액 부족");
        insertRepayment(1L, today.minusMonths(20), 350000, 280000, 70000, "PAID", null);
        insertRepayment(3L, today.minusMonths(1), 260000, 240000, 20000, "PAID", null);
    }

    @Test
    @WithMockUser(username = "1")
    void 본인_대출만_상태와_금리_순으로_조회한다() throws Exception {
        mockMvc.perform(get("/api/v1/loans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].accountNoMasked").value("110-***-****11"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].loanType").value("CREDIT"))
                .andExpect(jsonPath("$.data[0].repaymentType").value("EQUAL_PRINCIPAL"))
                .andExpect(jsonPath("$.data[0].balance").value(8000000))
                .andExpect(jsonPath("$.data[1].accountNoMasked").value("110-***-****22"))
                .andExpect(jsonPath("$.data[2].status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(username = "1")
    void 상태와_유형으로_대출을_거른다() throws Exception {
        mockMvc.perform(get("/api/v1/loans").param("status", "completed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].accountNoMasked").value("110-***-****44"));

        mockMvc.perform(get("/api/v1/loans").param("loanType", "JEONSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].loanType").value("JEONSE"));
    }

    @Test
    @WithMockUser(username = "1")
    void 알_수_없는_상태나_유형은_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/loans").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/loans").param("loanType", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @WithMockUser(username = "1")
    void 요약은_잔액이_남은_대출만_집계한다() throws Exception {
        mockMvc.perform(get("/api/v1/loans/summary"))
                .andExpect(status().isOk())
                // 완료된 4번 대출은 빠지고 1, 2번만 집계된다.
                .andExpect(jsonPath("$.data.loanCount").value(2))
                .andExpect(jsonPath("$.data.totalOriginalPrincipal").value(62000000))
                .andExpect(jsonPath("$.data.totalBalance").value(58000000))
                // 잔액 가중평균: (5.4 x 800만 + 3.2 x 5000만) / 5800만 = 3.5034
                .andExpect(jsonPath("$.data.weightedAverageRate").value(3.5034))
                .andExpect(jsonPath("$.data.loanTypes", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "3")
    void 대출이_없으면_빈_목록과_0_요약을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/loans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(get("/api/v1/loans/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loanCount").value(0))
                .andExpect(jsonPath("$.data.totalBalance").value(0))
                .andExpect(jsonPath("$.data.weightedAverageRate").value(nullValue()))
                .andExpect(jsonPath("$.data.loanTypes", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "1")
    void 대출_상세는_상환한_원금을_함께_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/loans/" + CREDIT_LOAN_NO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountNoMasked").value("110-***-****11"))
                .andExpect(jsonPath("$.data.institutionName").value("KB국민은행"))
                .andExpect(jsonPath("$.data.originalPrincipal").value(12000000))
                .andExpect(jsonPath("$.data.balance").value(8000000))
                .andExpect(jsonPath("$.data.repaidPrincipal").value(4000000))
                .andExpect(jsonPath("$.data.interestRate").value(5.4))
                .andExpect(jsonPath("$.data.loanPeriod").value(36));
    }

    @Test
    @WithMockUser(username = "1")
    void 타인_대출과_없는_대출은_모두_404다() throws Exception {
        mockMvc.perform(get("/api/v1/loans/" + OTHER_USER_LOAN_NO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/loans/" + UNKNOWN_LOAN_NO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 인증이_없으면_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/loans"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/loans/" + CREDIT_LOAN_NO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "1")
    void 상환내역은_기본_조회기간과_최신순_정렬을_따른다() throws Exception {
        // 기본 조회 기간은 3개월이라 5개월, 20개월 전 상환은 빠진다.
        mockMvc.perform(get("/api/v1/loans/" + CREDIT_LOAN_NO + "/repayments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].repaymentDate")
                        .value(today.minusMonths(1).toString()))
                .andExpect(jsonPath("$.data.content[0].principal").value(300000))
                .andExpect(jsonPath("$.data.content[0].status").value("PAID"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.page").value(0));
    }

    @Test
    @WithMockUser(username = "1")
    void 상환내역_기간과_페이지를_지정할_수_있다() throws Exception {
        mockMvc.perform(get("/api/v1/loans/" + CREDIT_LOAN_NO + "/repayments")
                        .param("startDate", today.minusMonths(6).toString())
                        .param("endDate", today.toString())
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.last").value(false));

        mockMvc.perform(get("/api/v1/loans/" + CREDIT_LOAN_NO + "/repayments")
                        .param("startDate", today.minusMonths(6).toString())
                        .param("endDate", today.toString())
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.content[0].failureReason").value("잔액 부족"))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    @WithMockUser(username = "1")
    void 상환내역_입력_검증이_동작한다() throws Exception {
        mockMvc.perform(get("/api/v1/loans/" + CREDIT_LOAN_NO + "/repayments")
                        .param("startDate", today.toString())
                        .param("endDate", today.minusMonths(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/loans/" + CREDIT_LOAN_NO + "/repayments")
                        .param("endDate", today.plusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/loans/" + CREDIT_LOAN_NO + "/repayments")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser(username = "1")
    void 상환내역이_없으면_빈_페이지다() throws Exception {
        mockMvc.perform(get("/api/v1/loans/" + JEONSE_LOAN_NO + "/repayments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @WithMockUser(username = "1")
    void 원금균등_상환일정을_만기까지_계산한다() throws Exception {
        mockMvc.perform(get("/api/v1/loans/" + CREDIT_LOAN_NO + "/repayment-schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.repaymentType").value("EQUAL_PRINCIPAL"))
                .andExpect(jsonPath("$.data.scheduleCount").value(12))
                .andExpect(jsonPath("$.data.truncated").value(false))
                // 원금 800만원을 12회로 나눈 첫 회차 원금과 월이율 0.45% 기준 첫 이자
                .andExpect(jsonPath("$.data.schedules[0].principal").value(666666))
                .andExpect(jsonPath("$.data.schedules[0].interest").value(36000))
                .andExpect(jsonPath("$.data.totalPrincipal").value(8000000))
                .andExpect(jsonPath("$.data.schedules[11].remainingBalance").value(0));
    }

    @Test
    @WithMockUser(username = "1")
    void 만기일시_상환일정은_마지막에_원금을_상환한다() throws Exception {
        mockMvc.perform(get("/api/v1/loans/" + JEONSE_LOAN_NO + "/repayment-schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.repaymentType").value("BULLET"))
                .andExpect(jsonPath("$.data.scheduleCount").value(6))
                .andExpect(jsonPath("$.data.schedules[0].principal").value(0))
                .andExpect(jsonPath("$.data.schedules[0].interest").value(133333))
                .andExpect(jsonPath("$.data.schedules[5].principal").value(50000000))
                .andExpect(jsonPath("$.data.totalPrincipal").value(50000000));
    }

    @Test
    @WithMockUser(username = "1")
    void 잔액이_없는_대출의_상환일정은_비어_있다() throws Exception {
        mockMvc.perform(get("/api/v1/loans/" + COMPLETED_LOAN_NO + "/repayment-schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scheduleCount").value(0))
                .andExpect(jsonPath("$.data.schedules", hasSize(0)))
                .andExpect(jsonPath("$.data.totalInterest").value(0));
    }

    @Test
    @WithMockUser(username = "1")
    void 계좌번호_형식이_아니면_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/loans/abcd"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/loans/123"))
                .andExpect(status().isBadRequest());
    }

    private void updateLoanHash(long loanId, String accountNo) {
        jdbcTemplate.update(
                "UPDATE loan SET account_no_hash = ? WHERE loan_id = ?",
                sensitiveNumberHasher.hash(accountNo),
                loanId
        );
    }

    private void updateLoanSchedule(
            long loanId,
            LocalDate nextRepaymentDate,
            LocalDate maturityDate
    ) {
        jdbcTemplate.update(
                "UPDATE loan SET next_repayment_date = ?, maturity_date = ? WHERE loan_id = ?",
                nextRepaymentDate,
                maturityDate,
                loanId
        );
    }

    private void insertRepayment(
            long loanId,
            LocalDate repaymentDate,
            long amount,
            long principal,
            long interest,
            String status,
            String failureReason
    ) {
        jdbcTemplate.update(
                "INSERT INTO loan_repayment"
                        + " (loan_id, repayment_date, amount, principal, interest, status,"
                        + " failure_reason) VALUES (?, ?, ?, ?, ?, ?, ?)",
                loanId,
                repaymentDate,
                amount,
                principal,
                interest,
                status,
                failureReason
        );
    }
}
