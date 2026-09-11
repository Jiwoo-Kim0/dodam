package com.ssafy.dodam.domain.transaction;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.auth.repository.AuthSessionRepository;
import com.ssafy.dodam.domain.finance.repository.FinanceConnectionRepository;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.domain.user.repository.UserSettingRepository;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyFinanceClient;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyMember;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
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
 * S15P21D105-507 소비·저축 비율, 소비 급증, 소비목표 비교, 고정·변동지출.
 *
 * <p>날짜 의존을 피하려고 달마다 거래는 1일에만 둔다. 기준 달 M(이번 달)에서 M-5까지 6개월치를 넣는다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = "/sql/transaction-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class SpendingAnalysisApiIntegrationTest {

    private static final String EMAIL = "analysis@example.com";
    private static final String OTHER_EMAIL = "other-analysis@example.com";
    private static final String PASSWORD = "password123";
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

    @MockitoBean
    private SsafyFinanceClient ssafyFinanceClient;

    private Long userId;
    private Long otherUserId;
    private String bearer;
    private LocalDate today;
    private YearMonth m0;

    @BeforeEach
    void setUp() throws Exception {
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        m0 = YearMonth.from(today);

        jdbcTemplate.update("DELETE FROM spending_goal");
        jdbcTemplate.update("DELETE FROM goal");
        jdbcTemplate.update("DELETE FROM `transaction`");
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
        otherUserId = userRepository.findByEmailAndDeletedAtIsNull(OTHER_EMAIL).orElseThrow().getId();
        bearer = login(EMAIL);

        // 이번 달(M0): 고정 500,000(주거) / 변동 60,000(식비 40,000 + 카페 20,000) / 미분류 30,000 / 저축성 300,000 / 수입 3,000,000
        insertTx("A-00-1", userId, m0, "EXPENSE", 500_000, 12L, "FIXED");
        insertTx("A-00-2", userId, m0, "EXPENSE", 40_000, 1L, "VARIABLE");
        insertTx("A-00-3", userId, m0, "EXPENSE", 20_000, 2L, "VARIABLE");
        insertTx("A-00-4", userId, m0, "EXPENSE", 30_000, null, null);
        insertTx("A-00-5", userId, m0, "EXPENSE", 300_000, 18L, "NON_SPENDING");
        insertTx("A-00-6", userId, m0, "INCOME", 3_000_000, 19L, "NON_SPENDING");
        // M-1, M-2: 고정 500,000 / 식비 40,000·38,000 / 카페 20,000·22,000  (최근 3개월 평균: 식비 39,333, 카페 20,667)
        insertTx("A-01-1", userId, m0.minusMonths(1), "EXPENSE", 500_000, 12L, "FIXED");
        insertTx("A-01-2", userId, m0.minusMonths(1), "EXPENSE", 40_000, 1L, "VARIABLE");
        insertTx("A-01-3", userId, m0.minusMonths(1), "EXPENSE", 20_000, 2L, "VARIABLE");
        insertTx("A-02-1", userId, m0.minusMonths(2), "EXPENSE", 500_000, 12L, "FIXED");
        insertTx("A-02-2", userId, m0.minusMonths(2), "EXPENSE", 38_000, 1L, "VARIABLE");
        insertTx("A-02-3", userId, m0.minusMonths(2), "EXPENSE", 22_000, 2L, "VARIABLE");
        // M-3 ~ M-5(이전 3개월): 고정 480,000 / 식비 25,000 / 카페 20,000 → 식비는 +57%, 카페는 +3%, 고정은 +4%
        for (int i = 3; i <= 5; i++) {
            insertTx("A-0" + i + "-1", userId, m0.minusMonths(i), "EXPENSE", 480_000, 12L, "FIXED");
            insertTx("A-0" + i + "-2", userId, m0.minusMonths(i), "EXPENSE", 25_000, 1L, "VARIABLE");
            insertTx("A-0" + i + "-3", userId, m0.minusMonths(i), "EXPENSE", 20_000, 2L, "VARIABLE");
        }
        // 타인
        insertTx("A-99", otherUserId, m0, "EXPENSE", 9_000_000, 1L, "VARIABLE");
    }

    // ---------- 소비 및 저축 비율 ----------

    @Test
    void savingRatioUsesIncomeTransactionsWhenProfileHasNoIncome() throws Exception {
        mockMvc.perform(get("/api/v1/spending/saving-ratio").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value(m0.toString()))
                .andExpect(jsonPath("$.data.income").value(3_000_000))
                .andExpect(jsonPath("$.data.incomeSource").value("TRANSACTIONS"))
                .andExpect(jsonPath("$.data.spending").value(590_000))
                .andExpect(jsonPath("$.data.savingTransfer").value(300_000))
                .andExpect(jsonPath("$.data.remaining").value(2_410_000))
                .andExpect(jsonPath("$.data.spendingRatioPercent").value(19.7))
                .andExpect(jsonPath("$.data.savingRatioPercent").value(80.3))
                .andExpect(jsonPath("$.data.targetSpendingRatioPercent").value(nullValue()))
                .andExpect(jsonPath("$.data.exceedsTarget").value(nullValue()));
    }

    @Test
    void savingRatioPrefersProfileIncomeAndComparesWithActiveGoals() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/financial-profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("monthlyIncome", 2_000_000))))
                .andExpect(status().isOk());
        insertSpendingGoal(1L, userId, "이번 달 50만 원", "ACTIVE", 500_000);
        insertSpendingGoal(2L, userId, "쉬는 목표", "PAUSED", 100_000_000);

        mockMvc.perform(get("/api/v1/spending/saving-ratio").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.income").value(2_000_000))
                .andExpect(jsonPath("$.data.incomeSource").value("PROFILE"))
                .andExpect(jsonPath("$.data.spendingRatioPercent").value(29.5))
                .andExpect(jsonPath("$.data.targetSpendingRatioPercent").value(25.0))
                .andExpect(jsonPath("$.data.targetSavingRatioPercent").value(75.0))
                .andExpect(jsonPath("$.data.exceedsTarget").value(true));
    }

    @Test
    void savingRatioWithoutAnyIncomeHasNullRatios() throws Exception {
        mockMvc.perform(get("/api/v1/spending/saving-ratio").param("yearMonth", m0.minusMonths(1).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.income").value(0))
                .andExpect(jsonPath("$.data.incomeSource").value("NONE"))
                .andExpect(jsonPath("$.data.spending").value(560_000))
                .andExpect(jsonPath("$.data.remaining").value(-560_000))
                .andExpect(jsonPath("$.data.spendingRatioPercent").value(nullValue()))
                .andExpect(jsonPath("$.data.savingRatioPercent").value(nullValue()));
    }

    // ---------- 소비 급증 ----------

    @Test
    void anomaliesCompareRecentThreeMonthsAgainstPreviousThree() throws Exception {
        mockMvc.perform(get("/api/v1/spending/anomalies").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentPeriod.startMonth").value(m0.minusMonths(2).toString()))
                .andExpect(jsonPath("$.data.recentPeriod.endMonth").value(m0.toString()))
                .andExpect(jsonPath("$.data.recentPeriod.months").value(3))
                .andExpect(jsonPath("$.data.baselinePeriod.startMonth").value(m0.minusMonths(5).toString()))
                .andExpect(jsonPath("$.data.baselinePeriod.endMonth").value(m0.minusMonths(3).toString()))
                .andExpect(jsonPath("$.data.thresholdRatePercent").value(20.0))
                .andExpect(jsonPath("$.data.minimumIncrease").value(10_000))
                .andExpect(jsonPath("$.data.evaluatedCategories").value(4))
                // 식비만: 39,333 vs 25,000 → +14,333(57.3%). 카페 +3.3%, 주거 +4.2%는 기준 미달, 미분류는 이전 기간 0
                .andExpect(jsonPath("$.data.anomalies", hasSize(1)))
                .andExpect(jsonPath("$.data.anomalies[0].code").value("FOOD"))
                .andExpect(jsonPath("$.data.anomalies[0].recentAverage").value(39_333))
                .andExpect(jsonPath("$.data.anomalies[0].baselineAverage").value(25_000))
                .andExpect(jsonPath("$.data.anomalies[0].increaseAmount").value(14_333))
                .andExpect(jsonPath("$.data.anomalies[0].increaseRatePercent").value(57.3));
    }

    @Test
    void anomaliesCanBeFilteredByCategoryAndPeriod() throws Exception {
        mockMvc.perform(get("/api/v1/spending/anomalies").param("category", "cafe")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("CAFE"))
                .andExpect(jsonPath("$.data.evaluatedCategories").value(1))
                .andExpect(jsonPath("$.data.anomalies", hasSize(0)));

        // 최근 1개월(M0) vs 이전 1개월(M-1): 식비 40,000 vs 40,000 → 없음. 미분류 30,000 vs 0 → 이전 기간 0이라 판정 제외
        mockMvc.perform(get("/api/v1/spending/anomalies")
                        .param("startMonth", m0.toString()).param("endMonth", m0.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentPeriod.months").value(1))
                .andExpect(jsonPath("$.data.baselinePeriod.startMonth").value(m0.minusMonths(1).toString()))
                .andExpect(jsonPath("$.data.anomalies", hasSize(0)));

        mockMvc.perform(get("/api/v1/spending/anomalies").param("category", "NOPE")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ---------- 소비목표 비교 ----------

    @Test
    void goalComparisonListsActiveGoalsWithUsageAndProjection() throws Exception {
        insertSpendingGoal(1L, userId, "월 100만 원", "ACTIVE", 1_000_000);
        insertSpendingGoal(2L, userId, "월 60만 원", "ACTIVE", 600_000);
        insertSpendingGoal(3L, userId, "월 50만 원", "ACTIVE", 500_000);
        insertSpendingGoal(4L, userId, "쉬는 목표", "PAUSED", 10_000);
        insertSpendingGoal(5L, otherUserId, "타인 목표", "ACTIVE", 10_000);

        int days = m0.lengthOfMonth();
        int elapsed = today.getDayOfMonth();
        long projected = Math.round(590_000.0 * days / elapsed);
        mockMvc.perform(get("/api/v1/spending/goal-comparison").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value(m0.toString()))
                .andExpect(jsonPath("$.data.asOfDate").value(today.toString()))
                .andExpect(jsonPath("$.data.daysElapsed").value(elapsed))
                .andExpect(jsonPath("$.data.spending").value(590_000))
                .andExpect(jsonPath("$.data.goals", hasSize(3)))
                .andExpect(jsonPath("$.data.goals[0].goalId").value(1))
                .andExpect(jsonPath("$.data.goals[0].monthlyBudget").value(1_000_000))
                .andExpect(jsonPath("$.data.goals[0].remaining").value(410_000))
                .andExpect(jsonPath("$.data.goals[0].usageRatePercent").value(59.0))
                .andExpect(jsonPath("$.data.goals[0].projectedMonthEnd").value(projected))
                .andExpect(jsonPath("$.data.goals[0].status").value("ON_TRACK"))
                .andExpect(jsonPath("$.data.goals[1].usageRatePercent").value(98.3))
                .andExpect(jsonPath("$.data.goals[1].status").value("WARNING"))
                .andExpect(jsonPath("$.data.goals[2].remaining").value(-90_000))
                .andExpect(jsonPath("$.data.goals[2].usageRatePercent").value(118.0))
                .andExpect(jsonPath("$.data.goals[2].status").value("EXCEEDED"))
                .andExpect(jsonPath("$.data.goals[2].dailyBudgetRemaining")
                        .value(days - elapsed > 0 ? 0 : null));
    }

    @Test
    void goalComparisonBySingleGoalIncludesPausedButNotOthers() throws Exception {
        insertSpendingGoal(4L, userId, "쉬는 목표", "PAUSED", 1_000_000);
        insertSpendingGoal(5L, otherUserId, "타인 목표", "ACTIVE", 10_000);

        mockMvc.perform(get("/api/v1/spending/goal-comparison").param("goalId", "4")
                        .param("yearMonth", m0.minusMonths(1).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.asOfDate").value(m0.minusMonths(1).atEndOfMonth().toString()))
                .andExpect(jsonPath("$.data.spending").value(560_000))
                .andExpect(jsonPath("$.data.goals", hasSize(1)))
                .andExpect(jsonPath("$.data.goals[0].goalStatus").value("PAUSED"))
                .andExpect(jsonPath("$.data.goals[0].projectedMonthEnd").value(560_000))
                .andExpect(jsonPath("$.data.goals[0].dailyBudgetRemaining").value(nullValue()));

        mockMvc.perform(get("/api/v1/spending/goal-comparison").param("goalId", "5")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("소비 목표를 찾을 수 없습니다."));

        mockMvc.perform(get("/api/v1/spending/goal-comparison").param("goalId", "0")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void goalComparisonWithoutGoalsIsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/spending/goal-comparison").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spending").value(590_000))
                .andExpect(jsonPath("$.data.goals", hasSize(0)));
    }

    // ---------- 고정·변동지출 ----------

    @Test
    void fixedSpendingBreakdownOverDefaultPeriod() throws Exception {
        mockMvc.perform(get("/api/v1/spending/fixed").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spendingType").value("FIXED"))
                .andExpect(jsonPath("$.data.startMonth").value(m0.minusMonths(2).toString()))
                .andExpect(jsonPath("$.data.totalAmount").value(1_500_000))
                .andExpect(jsonPath("$.data.monthlyAverage").value(500_000))
                // 전체 소비 1,500,000 + 120,000 + 60,000(식비·카페) + 30,000 = 1,710,000
                .andExpect(jsonPath("$.data.shareOfSpendingPercent").value(87.7))
                .andExpect(jsonPath("$.data.latestMonthAmount").value(500_000))
                .andExpect(jsonPath("$.data.previousAverage").value(500_000))
                .andExpect(jsonPath("$.data.changeRatePercent").value(0.0))
                .andExpect(jsonPath("$.data.months", hasSize(3)))
                .andExpect(jsonPath("$.data.months[0].amount").value(500_000))
                .andExpect(jsonPath("$.data.categories", hasSize(1)))
                .andExpect(jsonPath("$.data.categories[0].code").value("HOUSING"))
                .andExpect(jsonPath("$.data.categories[0].sharePercent").value(100.0));
    }

    @Test
    void variableSpendingBreakdownExcludesUncategorizedAndFixed() throws Exception {
        mockMvc.perform(get("/api/v1/spending/variable")
                        .param("startMonth", m0.minusMonths(5).toString()).param("endMonth", m0.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spendingType").value("VARIABLE"))
                .andExpect(jsonPath("$.data.months", hasSize(6)))
                .andExpect(jsonPath("$.data.months[0].amount").value(45_000))
                .andExpect(jsonPath("$.data.months[5].amount").value(60_000))
                .andExpect(jsonPath("$.data.totalAmount").value(315_000))
                .andExpect(jsonPath("$.data.monthlyAverage").value(52_500))
                .andExpect(jsonPath("$.data.latestMonthAmount").value(60_000))
                .andExpect(jsonPath("$.data.previousAverage").value(51_000))
                .andExpect(jsonPath("$.data.changeRatePercent").value(17.6))
                .andExpect(jsonPath("$.data.categories", hasSize(2)))
                .andExpect(jsonPath("$.data.categories[0].code").value("FOOD"))
                .andExpect(jsonPath("$.data.categories[0].amount").value(193_000))
                .andExpect(jsonPath("$.data.categories[1].code").value("CAFE"))
                .andExpect(jsonPath("$.data.categories[1].amount").value(122_000));
    }

    @Test
    void singleMonthBreakdownHasNoPreviousAverage() throws Exception {
        mockMvc.perform(get("/api/v1/spending/fixed")
                        .param("startMonth", m0.toString()).param("endMonth", m0.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.months", hasSize(1)))
                .andExpect(jsonPath("$.data.previousAverage").value(nullValue()))
                .andExpect(jsonPath("$.data.changeRatePercent").value(nullValue()));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/spending/saving-ratio")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/spending/anomalies")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/spending/goal-comparison")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/spending/fixed")).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private void insertTx(String id, Long ownerId, YearMonth month, String type, long amount, Long categoryId, String spendingType) {
        jdbcTemplate.update(
                """
                INSERT INTO `transaction` (
                    transaction_id, user_id, account_id, card_id, category_id,
                    transaction_date, transaction_time, transaction_type, amount, balance_after,
                    merchant_name, payment_method, spending_type, summary, memo,
                    hidden, source_type, created_at, updated_at
                ) VALUES (?, ?, NULL, NULL, ?, ?, NULL, ?, ?, NULL, NULL, 'CARD', ?, ?, NULL, FALSE, 'SEED', ?, NULL)
                """,
                id, ownerId, categoryId, month.atDay(1), type, amount, spendingType, id, SEEDED_AT
        );
    }

    private void insertSpendingGoal(Long goalId, Long ownerId, String name, String status, long monthlyBudget) {
        jdbcTemplate.update(
                "INSERT INTO goal (goal_id, user_id, name, type, status, current_amount, created_at, updated_at, deleted_at) "
                        + "VALUES (?, ?, ?, 'SPENDING', ?, 0, ?, ?, NULL)",
                goalId, ownerId, name, status, SEEDED_AT, SEEDED_AT
        );
        jdbcTemplate.update(
                "INSERT INTO spending_goal (goal_id, goal_type, monthly_budget) VALUES (?, 'SPENDING', ?)",
                goalId, monthlyBudget
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
