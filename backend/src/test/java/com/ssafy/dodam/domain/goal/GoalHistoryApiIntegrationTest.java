package com.ssafy.dodam.domain.goal;

import com.ssafy.dodam.domain.goal.service.GoalForecastFormula;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.auth.repository.AuthSessionRepository;
import com.ssafy.dodam.domain.finance.repository.FinanceConnectionRepository;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.domain.user.repository.UserSettingRepository;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyFinanceClient;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyMember;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * S15P21D105-511 목표 변화 이력: 어떤 변화가 어떤 event로 남는지, 조회(기간·페이지·최신순·소유권)를 본다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = "/sql/goal-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class GoalHistoryApiIntegrationTest {

    private static final String EMAIL = "goal-history@example.com";
    private static final String OTHER_EMAIL = "other-goal-history@example.com";
    private static final String PASSWORD = "password123";
    private static final String MAIN_NO = "1234567890123456";
    private static final long MAIN_ID = 11L;
    private static final LocalDateTime SEEDED_AT = LocalDateTime.of(2026, 9, 1, 9, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSettingRepository userSettingRepository;

    @Autowired
    private FinancialProfileRepository financialProfileRepository;

    @Autowired
    private FinanceConnectionRepository financeConnectionRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SensitiveNumberHasher sensitiveNumberHasher;

    @MockitoBean
    private SsafyFinanceClient ssafyFinanceClient;

    private Long userId;
    private String bearer;
    private String otherBearer;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        jdbcTemplate.update("DELETE FROM goal_history");
        jdbcTemplate.update("DELETE FROM goal_account");
        jdbcTemplate.update("DELETE FROM spending_goal");
        jdbcTemplate.update("DELETE FROM saving_goal");
        jdbcTemplate.update("DELETE FROM goal");
        jdbcTemplate.update("DELETE FROM account");
        authSessionRepository.deleteAll();
        financeConnectionRepository.deleteAll();
        userSettingRepository.deleteAll();
        financialProfileRepository.deleteAll();
        userRepository.deleteAll();

        given(ssafyFinanceClient.createMember(anyString()))
                .willAnswer(invocation -> new SsafyMember(
                        invocation.getArgument(0), "회원", "00100", "user-key-" + invocation.getArgument(0)));

        signup(EMAIL, "도담");
        signup(OTHER_EMAIL, "타인");
        userId = userRepository.findByEmailAndDeletedAtIsNull(EMAIL).orElseThrow().getId();
        bearer = login(EMAIL);
        otherBearer = login(OTHER_EMAIL);

        jdbcTemplate.update(
                """
                INSERT INTO account (
                    account_id, user_id, bank_code, bank_name, account_no_encrypted, account_no_hash,
                    account_no_masked, account_name, account_type, status, balance, last_transaction_date,
                    currency, source_type, created_at, updated_at
                ) VALUES (11, ?, '004', 'KB국민은행', X'00', ?, '123456-**-**3456', '주거래', 'DEMAND_DEPOSIT', 'ACTIVE',
                          2000000, NULL, 'KRW', 'SEED', ?, ?)
                """,
                userId, sensitiveNumberHasher.hash(MAIN_NO), SEEDED_AT, SEEDED_AT
        );
    }

    @Test
    void recordsEveryChangeOfASavingGoalNewestFirst() throws Exception {
        // 저축속도(694·702): 연결 입출금 계좌(11)의 직전 3개 완료 월 순현금흐름 → 월 50만. 연결 계좌 잔액 200만은 속도에 들어가지 않는다
        for (int back = 1; back <= 3; back++) {
            insertIncome(today.minusMonths(back).withDayOfMonth(10), 500_000);
        }
        long goalId = createSaving(10_000_000, today.plusMonths(10));
        // 1) 생성 → GOAL_CREATED (0원, 0%, 예상일 없음)
        // 2) 계좌 연결 → ACCOUNTS_UPDATED (200만, 20%, 남은 800만 / 50만 → 16개월 뒤)
        link(goalId, List.of(MAIN_ID));
        // 3) 잔액 변화 후 진행률 조회 → AMOUNT_UPDATED (300만, 30%)
        jdbcTemplate.update("UPDATE account SET balance = 3000000 WHERE account_id = 11");
        mockMvc.perform(get("/api/v1/goals/" + goalId + "/progress").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
        // 같은 잔액으로 다시 조회하면 이력이 늘지 않는다
        mockMvc.perform(get("/api/v1/goals/" + goalId + "/progress").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
        // 4) 목표 금액 수정 → GOAL_UPDATED (진행률 300/1500 = 20%)
        mockMvc.perform(patch("/api/v1/goals/" + goalId).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("targetAmount", 15_000_000))))
                .andExpect(status().isOk());
        // 값이 같은 수정은 이력이 없다
        mockMvc.perform(patch("/api/v1/goals/" + goalId).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("targetAmount", 15_000_000))))
                .andExpect(status().isOk());
        // 5) 상태 변경 → STATUS_CHANGED
        mockMvc.perform(patch("/api/v1/goals/" + goalId).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "PAUSED"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/goals/" + goalId + "/history").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.content", hasSize(5)))
                .andExpect(jsonPath("$.data.content[0].event").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$.data.content[0].currentAmount").value(3_000_000))
                .andExpect(jsonPath("$.data.content[0].progressRatePercent").value(20.0))
                .andExpect(jsonPath("$.data.content[1].event").value("GOAL_UPDATED"))
                .andExpect(jsonPath("$.data.content[1].progressRatePercent").value(20.0))
                .andExpect(jsonPath("$.data.content[2].event").value("AMOUNT_UPDATED"))
                .andExpect(jsonPath("$.data.content[2].currentAmount").value(3_000_000))
                .andExpect(jsonPath("$.data.content[2].progressRatePercent").value(30.0))
                // 남은 700만 / 50만 = 14개월. 510 forecast와 같은 식(GoalForecastFormula)
                .andExpect(jsonPath("$.data.content[2].forecastDate").value(GoalForecastFormula.expectedDate(7_000_000, 500_000, today).toString()))
                .andExpect(jsonPath("$.data.content[2].forecastDate").value(today.plusMonths(14).toString()))
                .andExpect(jsonPath("$.data.content[3].event").value("ACCOUNTS_UPDATED"))
                .andExpect(jsonPath("$.data.content[3].currentAmount").value(2_000_000))
                .andExpect(jsonPath("$.data.content[3].progressRatePercent").value(20.0))
                .andExpect(jsonPath("$.data.content[3].forecastDate").value(today.plusMonths(16).toString()))
                .andExpect(jsonPath("$.data.content[4].event").value("GOAL_CREATED"))
                .andExpect(jsonPath("$.data.content[4].currentAmount").value(0))
                .andExpect(jsonPath("$.data.content[4].progressRatePercent").value(0.0))
                .andExpect(jsonPath("$.data.content[4].forecastDate").value(nullValue()))
                .andExpect(jsonPath("$.data.content[4].recordedDate").value(today.toString()))
                .andExpect(jsonPath("$.data.content[4].createdAt").isNotEmpty());
    }

    @Test
    void spendingGoalHistoryHasNoProgressRateAndDeleteIsRecorded() throws Exception {
        long goalId = createSpending(500_000);
        mockMvc.perform(patch("/api/v1/goals/" + goalId).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("monthlyBudget", 400_000, "name", "새 이름"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/goals/" + goalId + "/history").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].event").value("GOAL_UPDATED"))
                .andExpect(jsonPath("$.data.content[0].progressRatePercent").value(nullValue()))
                .andExpect(jsonPath("$.data.content[0].forecastDate").value(nullValue()))
                .andExpect(jsonPath("$.data.content[1].event").value("GOAL_CREATED"));

        mockMvc.perform(delete("/api/v1/goals/" + goalId).header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());
        // 삭제된 목표의 이력은 API로는 404지만 행은 남는다
        mockMvc.perform(get("/api/v1/goals/" + goalId + "/history").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForList("SELECT event FROM goal_history WHERE goal_id = ? ORDER BY goal_history_id", String.class, goalId))
                .containsExactly("GOAL_CREATED", "GOAL_UPDATED", "GOAL_DELETED");
    }

    @Test
    void filtersByPeriodAndPages() throws Exception {
        long goalId = createSaving(10_000_000, today.plusMonths(10));
        // 과거 기록을 흉내 낸다
        insertHistory(goalId, today.minusMonths(2), 500_000, "AMOUNT_UPDATED");
        insertHistory(goalId, today.minusMonths(1), 1_000_000, "AMOUNT_UPDATED");
        insertHistory(goalId, today.minusMonths(5), 100_000, "AMOUNT_UPDATED");

        // 기본 기간(최근 3개월): 오늘 생성 + 2개월 전 + 1개월 전 = 3건, 최신순
        mockMvc.perform(get("/api/v1/goals/" + goalId + "/history").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].event").value("GOAL_CREATED"))
                .andExpect(jsonPath("$.data.content[1].recordedDate").value(today.minusMonths(1).toString()))
                .andExpect(jsonPath("$.data.content[2].recordedDate").value(today.minusMonths(2).toString()));

        // 기간을 넓히면 5개월 전 기록도 나온다
        mockMvc.perform(get("/api/v1/goals/" + goalId + "/history")
                        .param("startDate", today.minusMonths(6).toString()).param("endDate", today.toString())
                        .param("page", "1").param("size", "3")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].recordedDate").value(today.minusMonths(5).toString()))
                .andExpect(jsonPath("$.data.content[0].currentAmount").value(100_000));

        mockMvc.perform(get("/api/v1/goals/" + goalId + "/history")
                        .param("startDate", today.toString()).param("endDate", today.minusDays(1).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/goals/" + goalId + "/history").param("size", "101")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void hidesOtherUsersGoalHistoryAndRequiresAuthentication() throws Exception {
        long goalId = createSaving(10_000_000, today.plusMonths(10));
        mockMvc.perform(get("/api/v1/goals/" + goalId + "/history").header(HttpHeaders.AUTHORIZATION, otherBearer))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/goals/" + goalId + "/history")).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private void insertIncome(LocalDate date, long amount) {
        jdbcTemplate.update(
                """
                INSERT INTO `transaction` (transaction_id, user_id, account_id, transaction_date, transaction_type, amount,
                    payment_method, spending_type, summary, hidden, source_type, created_at)
                VALUES (?, ?, ?, ?, 'INCOME', ?, 'ACCOUNT', 'NON_SPENDING', 'salary', FALSE, 'SEED', ?)
                """, UUID.randomUUID().toString(), userId, MAIN_ID, date, amount, SEEDED_AT);
    }

    private long createSaving(long targetAmount, LocalDate targetDate) throws Exception {
        return create(Map.of("name", "모으기", "type", "SAVING", "targetAmount", targetAmount, "targetDate", targetDate.toString()));
    }

    private long createSpending(long monthlyBudget) throws Exception {
        return create(Map.of("name", "줄이기", "type", "SPENDING", "monthlyBudget", monthlyBudget));
    }

    private long create(Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/goals")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("goalId").asLong();
    }

    private void link(long goalId, List<Long> accountIds) throws Exception {
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountIds", accountIds))))
                .andExpect(status().isOk());
    }

    private void insertHistory(long goalId, LocalDate date, long amount, String event) {
        jdbcTemplate.update(
                "INSERT INTO goal_history (goal_id, recorded_date, current_amount, progress_rate, forecast_date, event, created_at) "
                        + "VALUES (?, ?, ?, NULL, NULL, ?, ?)",
                goalId, date, amount, event, date.atTime(9, 0)
        );
    }

    private void signup(String email, String name) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD, "name", name))))
                .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
