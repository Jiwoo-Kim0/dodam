package com.ssafy.dodam.domain.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
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
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
 * S15P21D105-510 진행률·필요 월 저축액·예상 달성일·소비목표 진행률·위험상태.
 *
 * <p>날짜 계산을 결정적으로 만들기 위해 목표의 created_at을 JDBC로 "오늘 − N개월 1일"로 되돌리고, 목표일은 오늘 기준 상대 날짜로 둔다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = "/sql/goal-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class GoalCalculationApiIntegrationTest {

    private static final String EMAIL = "goal-calc@example.com";
    private static final String PASSWORD = "password123";
    private static final String MAIN_NO = "1234567890123456";
    private static final String SAVINGS_NO = "3333333344444444";
    private static final long MAIN_ID = 11L;
    private static final long SAVINGS_ID = 12L;
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
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        jdbcTemplate.update("DELETE FROM `transaction`");
        jdbcTemplate.update("DELETE FROM savings_payment");
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
        userId = userRepository.findByEmailAndDeletedAtIsNull(EMAIL).orElseThrow().getId();
        bearer = login(EMAIL);

        insertAccount(11L, MAIN_NO, "DEMAND_DEPOSIT", 1_500_000);
        insertAccount(12L, SAVINGS_NO, "SAVINGS", 1_500_000);
    }

    // ---------- 진행률 ----------

    @Test
    void progressRecomputesCurrentAmountFromLinkedAccountsAndComparesWithSchedule() throws Exception {
        // 10개월 전에 만들어 20개월짜리(총 600일 내외) 목표. 기대 진행률 ≈ 50%, 실제 30%(300만/1000만) → BEHIND
        long goalId = createSaving(10_000_000, today.plusMonths(10));
        backdateCreatedAt(goalId, 10);
        link(goalId, List.of(MAIN_ID, SAVINGS_ID));
        // 연결 후 동기화로 잔액이 바뀐 상황
        jdbcTemplate.update("UPDATE account SET balance = 2000000 WHERE account_id = 11");

        LocalDate start = today.minusMonths(10);
        LocalDate target = today.plusMonths(10);
        long totalDays = ChronoUnit.DAYS.between(start, target);
        long elapsed = ChronoUnit.DAYS.between(start, today);
        double expected = Math.round(elapsed * 1000.0 / totalDays) / 10.0;

        mockMvc.perform(get("/api/v1/goals/" + goalId + "/progress").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetAmount").value(10_000_000))
                .andExpect(jsonPath("$.data.currentAmount").value(3_500_000))
                .andExpect(jsonPath("$.data.remainingAmount").value(6_500_000))
                .andExpect(jsonPath("$.data.progressRatePercent").value(35.0))
                .andExpect(jsonPath("$.data.startDate").value(start.toString()))
                .andExpect(jsonPath("$.data.targetDate").value(target.toString()))
                .andExpect(jsonPath("$.data.totalDays").value(totalDays))
                .andExpect(jsonPath("$.data.daysElapsed").value(elapsed))
                .andExpect(jsonPath("$.data.daysRemaining").value(ChronoUnit.DAYS.between(today, target)))
                .andExpect(jsonPath("$.data.expectedProgressRatePercent").value(expected))
                .andExpect(jsonPath("$.data.paceStatus").value("BEHIND"))
                .andExpect(jsonPath("$.data.linkedAccountCount").value(2));

        // 저장된 모인 금액도 갱신된다
        assertThat(jdbcTemplate.queryForObject("SELECT current_amount FROM goal WHERE goal_id = ?", Long.class, goalId))
                .isEqualTo(3_500_000L);
    }

    @Test
    void progressReportsAchievedAndAheadStates() throws Exception {
        long achieved = createSaving(2_000_000, today.plusMonths(6));
        link(achieved, List.of(MAIN_ID, SAVINGS_ID));
        mockMvc.perform(get("/api/v1/goals/" + achieved + "/progress").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressRatePercent").value(100.0))
                .andExpect(jsonPath("$.data.remainingAmount").value(0))
                .andExpect(jsonPath("$.data.paceStatus").value("ACHIEVED"));
        unlinkAll(achieved);

        // 방금 만든 목표(기대 0%)에 30% 확보 → AHEAD
        long ahead = createSaving(10_000_000, today.plusMonths(12));
        link(ahead, List.of(MAIN_ID, SAVINGS_ID));
        mockMvc.perform(get("/api/v1/goals/" + ahead + "/progress").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expectedProgressRatePercent").value(0.0))
                .andExpect(jsonPath("$.data.paceStatus").value("AHEAD"));
    }

    @Test
    void progressRejectsSpendingGoalAndHidesOthers() throws Exception {
        long spending = createSpending(500_000);
        mockMvc.perform(get("/api/v1/goals/" + spending + "/progress").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("모으기 목표에서만 조회할 수 있습니다."));
        mockMvc.perform(get("/api/v1/goals/999999/progress").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
    }

    // ---------- 필요 월 저축액 ----------

    @Test
    void requiredSavingDividesRemainingByRemainingMonthsAndComparesWithPace() throws Exception {
        // 3개월 전 생성, 목표일은 오늘 + 6개월 + 10일 → 남은 개월 7(올림). 확보 300만.
        // 속도(694)는 확보액이 아니라 연결 적금의 직전 3개 완료 월 납입 → 월 100만
        long goalId = createSaving(10_000_000, today.plusMonths(6).plusDays(10));
        backdateCreatedAt(goalId, 3);
        link(goalId, List.of(MAIN_ID, SAVINGS_ID));
        insertMonthlyPayments(SAVINGS_ID, 1_000_000);
        mockMvc.perform(patch("/api/v1/users/me/financial-profile").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("monthlyIncome", 4_000_000))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/goals/" + goalId + "/required-saving").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingAmount").value(7_000_000))
                .andExpect(jsonPath("$.data.monthsRemaining").value(7))
                .andExpect(jsonPath("$.data.requiredMonthlySaving").value(1_000_000))
                .andExpect(jsonPath("$.data.monthsElapsed").value(3))
                .andExpect(jsonPath("$.data.averageMonthlySaving").value(1_000_000))
                .andExpect(jsonPath("$.data.monthlyShortfall").value(0))
                .andExpect(jsonPath("$.data.feasibility").value("SUFFICIENT"))
                .andExpect(jsonPath("$.data.requiredRatioOfIncomePercent").value(25.0))
                .andExpect(jsonPath("$.data.daysRemaining").value(ChronoUnit.DAYS.between(today, today.plusMonths(6).plusDays(10))));
    }

    @Test
    void requiredSavingFlagsInsufficientPaceAndPassedDeadline() throws Exception {
        // 1개월 경과, 300만 확보, 적금 납입 월 300만, 남은 700만을 2개월 안에 → 필요 350만 → INSUFFICIENT
        long goalId = createSaving(10_000_000, today.plusMonths(2));
        backdateCreatedAt(goalId, 1);
        link(goalId, List.of(MAIN_ID, SAVINGS_ID));
        insertMonthlyPayments(SAVINGS_ID, 3_000_000);
        mockMvc.perform(get("/api/v1/goals/" + goalId + "/required-saving").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthsRemaining").value(2))
                .andExpect(jsonPath("$.data.requiredMonthlySaving").value(3_500_000))
                .andExpect(jsonPath("$.data.averageMonthlySaving").value(3_000_000))
                .andExpect(jsonPath("$.data.monthlyShortfall").value(500_000))
                .andExpect(jsonPath("$.data.feasibility").value("INSUFFICIENT"))
                .andExpect(jsonPath("$.data.requiredRatioOfIncomePercent").value(nullValue()));

        // 목표일이 지난 목표(생성은 API 제약으로 미래만 가능하므로 JDBC로 되돌린다)
        jdbcTemplate.update("UPDATE saving_goal SET target_date = ? WHERE goal_id = ?", today.minusDays(1), goalId);
        mockMvc.perform(get("/api/v1/goals/" + goalId + "/required-saving").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthsRemaining").value(0))
                .andExpect(jsonPath("$.data.requiredMonthlySaving").value(7_000_000))
                .andExpect(jsonPath("$.data.requiredDailySaving").value(7_000_000))
                .andExpect(jsonPath("$.data.feasibility").value("DEADLINE_PASSED"));
    }

    // ---------- 예상 달성일 ----------

    @Test
    void forecastProjectsAchievementDateFromCurrentPace() throws Exception {
        // 3개월 경과, 300만 확보, 적금 납입 월 100만. 남은 700만 → 7개월 뒤 달성. 목표일 오늘+6개월 → 1개월 늦음
        long goalId = createSaving(10_000_000, today.plusMonths(6));
        backdateCreatedAt(goalId, 3);
        link(goalId, List.of(MAIN_ID, SAVINGS_ID));
        insertMonthlyPayments(SAVINGS_ID, 1_000_000);

        LocalDate expected = today.plusMonths(7);
        mockMvc.perform(get("/api/v1/goals/" + goalId + "/forecast").param("months", "8")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseDate").value(today.toString()))
                .andExpect(jsonPath("$.data.averageMonthlySaving").value(1_000_000))
                .andExpect(jsonPath("$.data.monthsToAchieve").value(7))
                .andExpect(jsonPath("$.data.expectedAchievementDate").value(expected.toString()))
                .andExpect(jsonPath("$.data.daysDifference").value(ChronoUnit.DAYS.between(today.plusMonths(6), expected)))
                .andExpect(jsonPath("$.data.onSchedule").value(false))
                .andExpect(jsonPath("$.data.projection", hasSize(8)))
                .andExpect(jsonPath("$.data.projection[0].yearMonth").value(YearMonth.from(today.plusMonths(1)).toString()))
                .andExpect(jsonPath("$.data.projection[0].projectedAmount").value(4_000_000))
                .andExpect(jsonPath("$.data.projection[0].achieved").value(false))
                .andExpect(jsonPath("$.data.projection[6].projectedAmount").value(10_000_000))
                .andExpect(jsonPath("$.data.projection[6].achieved").value(true))
                .andExpect(jsonPath("$.data.projection[7].projectedAmount").value(10_000_000));

        // 기준일을 바꾸면 예상일도 그 기준으로 밀린다
        LocalDate base = today.plusMonths(1);
        mockMvc.perform(get("/api/v1/goals/" + goalId + "/forecast").param("baseDate", base.toString()).param("months", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expectedAchievementDate").value(base.plusMonths(7).toString()))
                .andExpect(jsonPath("$.data.projection", hasSize(1)));
    }

    @Test
    void forecastWithoutPaceHasNoExpectedDateAndValidatesParams() throws Exception {
        long goalId = createSaving(10_000_000, today.plusMonths(6));
        mockMvc.perform(get("/api/v1/goals/" + goalId + "/forecast").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageMonthlySaving").value(0))
                .andExpect(jsonPath("$.data.expectedAchievementDate").value(nullValue()));

        // 694: 잔액이 있는 계좌를 방금 만든 목표에 연결해도 그 잔액이 "한 달 저축액"이 되지 않는다. 납입·순현금흐름이 없으면 속도 0
        link(goalId, List.of(MAIN_ID, SAVINGS_ID));
        mockMvc.perform(get("/api/v1/goals/" + goalId + "/forecast").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentAmount").value(3_000_000))
                .andExpect(jsonPath("$.data.averageMonthlySaving").value(0))
                .andExpect(jsonPath("$.data.expectedAchievementDate").value(nullValue()));
        unlinkAll(goalId);

        mockMvc.perform(get("/api/v1/goals/" + goalId + "/forecast").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageMonthlySaving").value(0))
                .andExpect(jsonPath("$.data.monthsToAchieve").value(nullValue()))
                .andExpect(jsonPath("$.data.expectedAchievementDate").value(nullValue()))
                .andExpect(jsonPath("$.data.daysDifference").value(nullValue()))
                .andExpect(jsonPath("$.data.onSchedule").value(false))
                .andExpect(jsonPath("$.data.projection", hasSize(12)))
                .andExpect(jsonPath("$.data.projection[11].projectedAmount").value(0));

        mockMvc.perform(get("/api/v1/goals/" + goalId + "/forecast").param("months", "0")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/goals/" + goalId + "/forecast").param("baseDate", "2026/01/01")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ---------- 소비목표 진행률 ----------

    @Test
    void spendingProgressComparesThisMonthSpendingWithBudget() throws Exception {
        long goalId = createSpending(500_000);
        YearMonth month = YearMonth.from(today);
        insertExpense("SP-1", month.atDay(1), 300_000);
        insertExpense("SP-2", month.atDay(1), 50_000);
        insertExpense("SP-PREV", month.minusMonths(1).atDay(5), 600_000);
        // 소비가 아닌 것: 수입·숨김
        jdbcTemplate.update("UPDATE `transaction` SET hidden = TRUE WHERE transaction_id = 'SP-2'");

        int elapsed = today.getDayOfMonth();
        int days = month.lengthOfMonth();
        long projected = Math.round(300_000.0 * days / elapsed);
        mockMvc.perform(get("/api/v1/goals/" + goalId + "/spending-progress").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value(month.toString()))
                .andExpect(jsonPath("$.data.asOfDate").value(today.toString()))
                .andExpect(jsonPath("$.data.daysElapsed").value(elapsed))
                .andExpect(jsonPath("$.data.daysInMonth").value(days))
                .andExpect(jsonPath("$.data.daysLeft").value(days - elapsed))
                .andExpect(jsonPath("$.data.monthlyBudget").value(500_000))
                .andExpect(jsonPath("$.data.spent").value(300_000))
                .andExpect(jsonPath("$.data.remainingBudget").value(200_000))
                .andExpect(jsonPath("$.data.usageRatePercent").value(60.0))
                .andExpect(jsonPath("$.data.projectedMonthEnd").value(projected))
                .andExpect(jsonPath("$.data.previousMonthSpending").value(600_000))
                .andExpect(jsonPath("$.data.expectedSavingAmount").value(Math.max(0, 600_000 - projected)))
                .andExpect(jsonPath("$.data.status").value("ON_TRACK"));

        long saving = createSaving(1_000_000, today.plusMonths(3));
        mockMvc.perform(get("/api/v1/goals/" + saving + "/spending-progress").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("줄이기 목표에서만 조회할 수 있습니다."));
    }

    // ---------- 위험상태 ----------

    @Test
    void savingGoalRiskLevelsAndFactors() throws Exception {
        // 방금 만든 목표, 계좌 없음, 필요 월 저축액 > 0인데 속도 0 → PACE_INSUFFICIENT(100% 부족 → HIGH) + NO_LINKED_ACCOUNTS
        long fresh = createSaving(10_000_000, today.plusMonths(10));
        mockMvc.perform(get("/api/v1/goals/" + fresh + "/risk").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("SAVING"))
                .andExpect(jsonPath("$.data.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.factors").value(hasItem("NO_LINKED_ACCOUNTS")))
                .andExpect(jsonPath("$.data.factors").value(hasItem("PACE_INSUFFICIENT")))
                .andExpect(jsonPath("$.data.metrics.requiredMonthlySaving").value(1_000_000))
                .andExpect(jsonPath("$.data.metrics.averageMonthlySaving").value(0));

        // 3개월 경과, 300만 확보, 적금 납입 월 100만, 남은 700만/7개월 = 100만 → 속도 충분, 기대 진행률(≈33%) vs 실제 30% → LOW
        long steady = createSaving(10_000_000, today.plusMonths(6).plusDays(10));
        backdateCreatedAt(steady, 3);
        link(steady, List.of(MAIN_ID, SAVINGS_ID));
        insertMonthlyPayments(SAVINGS_ID, 1_000_000);
        mockMvc.perform(get("/api/v1/goals/" + steady + "/risk").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.data.factors", hasSize(0)));
        unlinkAll(steady);

        // 목표일이 지났고 남은 금액 있음 → HIGH, DEADLINE_PASSED
        long passed = createSaving(10_000_000, today.plusDays(1));
        link(passed, List.of(MAIN_ID));
        jdbcTemplate.update("UPDATE saving_goal SET target_date = ? WHERE goal_id = ?", today.minusDays(1), passed);
        mockMvc.perform(get("/api/v1/goals/" + passed + "/risk").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.factors").value(hasItem("DEADLINE_PASSED")));
        unlinkAll(passed);

        // 종료 상태 → NONE
        long canceled = createSaving(10_000_000, today.plusMonths(3));
        mockMvc.perform(patch("/api/v1/goals/" + canceled).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "CANCELED"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/goals/" + canceled + "/risk").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskLevel").value("NONE"))
                .andExpect(jsonPath("$.data.factors", hasSize(0)));
    }

    @Test
    void spendingGoalRiskLevels() throws Exception {
        long goalId = createSpending(500_000);
        YearMonth month = YearMonth.from(today);

        // 소비 없음 → LOW
        mockMvc.perform(get("/api/v1/goals/" + goalId + "/risk").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("SPENDING"))
                .andExpect(jsonPath("$.data.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.data.metrics.monthlyBudget").value(500_000))
                .andExpect(jsonPath("$.data.metrics.spent").value(0));

        // 예산 초과 → HIGH, OVER_BUDGET
        insertExpense("RK-1", month.atDay(1), 600_000);
        mockMvc.perform(get("/api/v1/goals/" + goalId + "/risk").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.factors").value(hasItem("OVER_BUDGET")))
                .andExpect(jsonPath("$.data.metrics.usageRatePercent").value(120.0));
    }

    // ---------- helpers ----------

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

    /** 목표 생성일을 오늘 − months 로 되돌린다(경과 개월 계산용). */
    /** 직전 3개 완료 월의 15일에 PAID 납입을 넣는다 → 저축속도(694) = monthly. deposit_saving 행이 없으면 창 전체를 이력으로 본다 */
    private void insertMonthlyPayments(long accountId, long monthly) {
        for (int back = 1; back <= 3; back++) {
            jdbcTemplate.update(
                    "INSERT INTO savings_payment (deposit_saving_id, account_type, payment_date, amount, installment_number, status) "
                            + "VALUES (?, 'SAVINGS', ?, ?, ?, 'PAID')",
                    accountId, today.minusMonths(back).withDayOfMonth(15), monthly, 4 - back);
        }
    }

    private void backdateCreatedAt(long goalId, int months) {
        LocalDateTime created = today.minusMonths(months).atTime(9, 0);
        jdbcTemplate.update("UPDATE goal SET created_at = ?, updated_at = ? WHERE goal_id = ?", created, created, goalId);
    }

    private void link(long goalId, List<Long> accountIds) throws Exception {
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountIds", accountIds))))
                .andExpect(status().isOk());
    }

    private void unlinkAll(long goalId) throws Exception {
        link(goalId, List.of());
    }

    private void insertAccount(Long accountId, String accountNo, String type, long balance) {
        jdbcTemplate.update(
                """
                INSERT INTO account (
                    account_id, user_id, bank_code, bank_name, account_no_encrypted, account_no_hash,
                    account_no_masked, account_name, account_type, status, balance, last_transaction_date,
                    currency, source_type, created_at, updated_at
                ) VALUES (?, ?, '004', 'KB국민은행', X'00', ?, '123456-**-**3456', '계좌', ?, 'ACTIVE', ?, NULL, 'KRW', 'SEED', ?, ?)
                """,
                accountId, userId, sensitiveNumberHasher.hash(accountNo), type, balance, SEEDED_AT, SEEDED_AT
        );
    }

    private void insertExpense(String id, LocalDate date, long amount) {
        jdbcTemplate.update(
                """
                INSERT INTO `transaction` (
                    transaction_id, user_id, account_id, card_id, category_id,
                    transaction_date, transaction_time, transaction_type, amount, balance_after,
                    merchant_name, payment_method, spending_type, summary, memo,
                    hidden, source_type, created_at, updated_at
                ) VALUES (?, ?, NULL, NULL, NULL, ?, NULL, 'EXPENSE', ?, NULL, NULL, 'CARD', 'VARIABLE', ?, NULL, FALSE, 'SEED', ?, NULL)
                """,
                id, userId, date, amount, id, SEEDED_AT
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
