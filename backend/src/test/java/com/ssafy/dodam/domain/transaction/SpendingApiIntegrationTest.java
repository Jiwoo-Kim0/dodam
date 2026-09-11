package com.ssafy.dodam.domain.transaction;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * S15P21D105-506 소비 기간·카테고리 집계. "소비" 정의(EXPENSE, NON_SPENDING 제외, 숨김 제외)가 모든 API에 같이 적용되는지 본다.
 *
 * <p>날짜 의존을 피하려고 "이번 달"은 항상 1일 거래로 두고, 오늘이 1일이어도 성립하도록 설계했다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = "/sql/transaction-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class SpendingApiIntegrationTest {

    private static final String EMAIL = "spending@example.com";
    private static final String OTHER_EMAIL = "other-spending@example.com";
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
    private YearMonth thisMonth;
    private YearMonth lastMonth;

    @BeforeEach
    void setUp() throws Exception {
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        thisMonth = YearMonth.from(today);
        lastMonth = thisMonth.minusMonths(1);

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

        LocalDate d1 = thisMonth.atDay(1);
        // 이번 달 소비: 식비 12,000 + 8,000(CARD), 카페 4,500(CARD), 미분류 30,000(CASH), 주거 500,000(ACCOUNT) = 554,500 / 5건
        insertTx("S-01", userId, d1, "EXPENSE", 12_000, 1L, "VARIABLE", "CARD", false);
        insertTx("S-02", userId, d1, "EXPENSE", 8_000, 1L, "VARIABLE", "CARD", false);
        insertTx("S-03", userId, d1, "EXPENSE", 4_500, 2L, "VARIABLE", "CARD", false);
        insertTx("S-04", userId, d1, "EXPENSE", 30_000, null, null, "CASH", false);
        insertTx("S-05", userId, d1, "EXPENSE", 500_000, 12L, "FIXED", "ACCOUNT", false);
        // 소비가 아닌 것: 수입, 이체(TRANSFER), 저축성 지출(NON_SPENDING), 숨긴 거래, 결제수단 없는 소비는 UNKNOWN 묶음
        insertTx("S-06", userId, d1, "INCOME", 3_000_000, 19L, "NON_SPENDING", "ACCOUNT", false);
        insertTx("S-07", userId, d1, "TRANSFER", 200_000, 18L, "NON_SPENDING", "ACCOUNT", false);
        insertTx("S-08", userId, d1, "EXPENSE", 300_000, 18L, "NON_SPENDING", "ACCOUNT", false);
        insertTx("S-09", userId, d1, "EXPENSE", 99_000, 1L, "VARIABLE", "CARD", true);
        // 지난 달 소비: 식비 20,000 + 카페 5,000 = 25,000 / 2건 (다른 날짜 두 개)
        insertTx("S-10", userId, lastMonth.atDay(3), "EXPENSE", 20_000, 1L, "VARIABLE", "CARD", false);
        insertTx("S-11", userId, lastMonth.atDay(10), "EXPENSE", 5_000, 2L, "VARIABLE", null, false);
        // 타인 소비
        insertTx("S-99", otherUserId, d1, "EXPENSE", 9_999_999, 1L, "VARIABLE", "CARD", false);
    }

    // ---------- 월 소비 요약 ----------

    @Test
    void monthlySummaryCountsOnlySpendingAndComparesWithPreviousMonth() throws Exception {
        mockMvc.perform(get("/api/v1/spending/monthly-summary").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value(thisMonth.toString()))
                .andExpect(jsonPath("$.data.totalSpending").value(554_500))
                .andExpect(jsonPath("$.data.transactionCount").value(5))
                .andExpect(jsonPath("$.data.daysElapsed").value(today.getDayOfMonth()))
                .andExpect(jsonPath("$.data.daysInMonth").value(thisMonth.lengthOfMonth()))
                .andExpect(jsonPath("$.data.dailyAverage").value(Math.round(554_500.0 / today.getDayOfMonth())))
                .andExpect(jsonPath("$.data.averagePerTransaction").value(110_900))
                .andExpect(jsonPath("$.data.previousMonthSpending").value(25_000))
                .andExpect(jsonPath("$.data.changeFromPreviousMonth").value(529_500))
                .andExpect(jsonPath("$.data.changeRatePercent").value(2118.0));
    }

    @Test
    void monthlySummaryOfPastMonthUsesWholeMonthAndNullRateWhenNoPreviousSpending() throws Exception {
        mockMvc.perform(get("/api/v1/spending/monthly-summary").param("yearMonth", lastMonth.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSpending").value(25_000))
                .andExpect(jsonPath("$.data.transactionCount").value(2))
                .andExpect(jsonPath("$.data.daysElapsed").value(lastMonth.lengthOfMonth()))
                .andExpect(jsonPath("$.data.averagePerTransaction").value(12_500))
                .andExpect(jsonPath("$.data.previousMonthSpending").value(0))
                .andExpect(jsonPath("$.data.changeRatePercent").value(nullValue()));
    }

    @Test
    void rejectsFutureMonth() throws Exception {
        mockMvc.perform(get("/api/v1/spending/monthly-summary").param("yearMonth", thisMonth.plusMonths(1).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ---------- 월 누적 소비 ----------

    @Test
    void progressAccumulatesDayByDayUntilTodayAndProjectsMonthEnd() throws Exception {
        long dailyAverage = Math.round(554_500.0 / today.getDayOfMonth());
        mockMvc.perform(get("/api/v1/spending/progress").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value(thisMonth.toString()))
                .andExpect(jsonPath("$.data.asOfDate").value(today.toString()))
                .andExpect(jsonPath("$.data.daysElapsed").value(today.getDayOfMonth()))
                .andExpect(jsonPath("$.data.days", hasSize(today.getDayOfMonth())))
                .andExpect(jsonPath("$.data.days[0].date").value(thisMonth.atDay(1).toString()))
                .andExpect(jsonPath("$.data.days[0].dailySpending").value(554_500))
                .andExpect(jsonPath("$.data.days[0].transactionCount").value(5))
                .andExpect(jsonPath("$.data.days[0].cumulativeSpending").value(554_500))
                .andExpect(jsonPath("$.data.days[-1].cumulativeSpending").value(554_500))
                .andExpect(jsonPath("$.data.totalSpending").value(554_500))
                .andExpect(jsonPath("$.data.dailyAverage").value(dailyAverage))
                .andExpect(jsonPath("$.data.projectedMonthEnd")
                        .value(Math.round(554_500.0 * thisMonth.lengthOfMonth() / today.getDayOfMonth())));
    }

    @Test
    void progressOfPastMonthCoversWholeMonth() throws Exception {
        mockMvc.perform(get("/api/v1/spending/progress").param("yearMonth", lastMonth.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.asOfDate").value(lastMonth.atEndOfMonth().toString()))
                .andExpect(jsonPath("$.data.days", hasSize(lastMonth.lengthOfMonth())))
                .andExpect(jsonPath("$.data.days[2].dailySpending").value(20_000))
                .andExpect(jsonPath("$.data.days[9].cumulativeSpending").value(25_000))
                .andExpect(jsonPath("$.data.totalSpending").value(25_000))
                .andExpect(jsonPath("$.data.projectedMonthEnd").value(25_000));
    }

    // ---------- 일별 소비 ----------

    @Test
    void dailyFillsEveryDayInPeriod() throws Exception {
        LocalDate start = lastMonth.atDay(1);
        mockMvc.perform(get("/api/v1/spending/daily")
                        .param("startDate", start.toString())
                        .param("endDate", lastMonth.atDay(10).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate").value(start.toString()))
                .andExpect(jsonPath("$.data.days", hasSize(10)))
                .andExpect(jsonPath("$.data.days[0].totalSpending").value(0))
                .andExpect(jsonPath("$.data.days[2].totalSpending").value(20_000))
                .andExpect(jsonPath("$.data.days[9].totalSpending").value(5_000))
                .andExpect(jsonPath("$.data.days[9].transactionCount").value(1))
                .andExpect(jsonPath("$.data.totalSpending").value(25_000))
                .andExpect(jsonPath("$.data.transactionCount").value(2))
                .andExpect(jsonPath("$.data.dailyAverage").value(2_500));
    }

    @Test
    void dailyDefaultPeriodIsThreeMonthsAndRejectsInvalidRange() throws Exception {
        mockMvc.perform(get("/api/v1/spending/daily").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.endDate").value(today.toString()))
                .andExpect(jsonPath("$.data.startDate").value(today.minusMonths(3).plusDays(1).toString()))
                .andExpect(jsonPath("$.data.totalSpending").value(579_500));

        mockMvc.perform(get("/api/v1/spending/daily")
                        .param("startDate", today.toString())
                        .param("endDate", today.minusDays(1).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ---------- 카테고리별 소비 ----------

    @Test
    void groupsSpendingByCategoryWithUncategorizedBucketAndShares() throws Exception {
        mockMvc.perform(get("/api/v1/spending/categories").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value(thisMonth.toString()))
                .andExpect(jsonPath("$.data.totalSpending").value(554_500))
                .andExpect(jsonPath("$.data.transactionCount").value(5))
                .andExpect(jsonPath("$.data.categories", hasSize(4)))
                .andExpect(jsonPath("$.data.categories[0].code").value("HOUSING"))
                .andExpect(jsonPath("$.data.categories[0].totalSpending").value(500_000))
                .andExpect(jsonPath("$.data.categories[0].sharePercent").value(90.2))
                .andExpect(jsonPath("$.data.categories[1].categoryId").value(nullValue()))
                .andExpect(jsonPath("$.data.categories[1].code").value("UNCATEGORIZED"))
                .andExpect(jsonPath("$.data.categories[1].name").value("미분류"))
                .andExpect(jsonPath("$.data.categories[1].totalSpending").value(30_000))
                .andExpect(jsonPath("$.data.categories[2].code").value("FOOD"))
                .andExpect(jsonPath("$.data.categories[2].totalSpending").value(20_000))
                .andExpect(jsonPath("$.data.categories[2].transactionCount").value(2))
                .andExpect(jsonPath("$.data.categories[3].code").value("CAFE"))
                .andExpect(jsonPath("$.data.categories[3].sharePercent").value(0.8));
    }

    @Test
    void categoriesOfMonthWithoutSpendingIsEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/spending/categories").param("yearMonth", thisMonth.minusMonths(2).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSpending").value(0))
                .andExpect(jsonPath("$.data.categories", hasSize(0)));
    }

    // ---------- 카테고리 소비 추이 ----------

    @Test
    void categoryTrendFillsMonthsAndListsCategoriesPerMonth() throws Exception {
        mockMvc.perform(get("/api/v1/spending/categories/trend").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startMonth").value(thisMonth.minusMonths(2).toString()))
                .andExpect(jsonPath("$.data.endMonth").value(thisMonth.toString()))
                .andExpect(jsonPath("$.data.category").value(nullValue()))
                .andExpect(jsonPath("$.data.months", hasSize(3)))
                .andExpect(jsonPath("$.data.months[0].totalSpending").value(0))
                .andExpect(jsonPath("$.data.months[0].categories", hasSize(0)))
                .andExpect(jsonPath("$.data.months[1].totalSpending").value(25_000))
                .andExpect(jsonPath("$.data.months[1].categories[0].code").value("FOOD"))
                .andExpect(jsonPath("$.data.months[1].categories[1].code").value("CAFE"))
                .andExpect(jsonPath("$.data.months[2].totalSpending").value(554_500))
                .andExpect(jsonPath("$.data.months[2].categories", hasSize(4)))
                .andExpect(jsonPath("$.data.totalSpending").value(579_500))
                .andExpect(jsonPath("$.data.monthlyAverage").value(193_167));
    }

    @Test
    void categoryTrendCanBeFilteredByCodeIncludingUncategorized() throws Exception {
        mockMvc.perform(get("/api/v1/spending/categories/trend").param("category", "food")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("FOOD"))
                .andExpect(jsonPath("$.data.months[1].totalSpending").value(20_000))
                .andExpect(jsonPath("$.data.months[2].totalSpending").value(20_000))
                .andExpect(jsonPath("$.data.months[2].categories", hasSize(1)))
                .andExpect(jsonPath("$.data.totalSpending").value(40_000));

        mockMvc.perform(get("/api/v1/spending/categories/trend").param("category", "UNCATEGORIZED")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.months[2].totalSpending").value(30_000));

        mockMvc.perform(get("/api/v1/spending/categories/trend").param("category", "NOPE")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("존재하지 않는 카테고리 코드입니다."));
    }

    // ---------- 결제수단별 소비 ----------

    @Test
    void groupsSpendingByPaymentMethod() throws Exception {
        mockMvc.perform(get("/api/v1/spending/payment-methods").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSpending").value(554_500))
                .andExpect(jsonPath("$.data.paymentMethods", hasSize(3)))
                .andExpect(jsonPath("$.data.paymentMethods[0].paymentMethod").value("ACCOUNT"))
                .andExpect(jsonPath("$.data.paymentMethods[0].totalSpending").value(500_000))
                .andExpect(jsonPath("$.data.paymentMethods[1].paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.data.paymentMethods[2].paymentMethod").value("CARD"))
                .andExpect(jsonPath("$.data.paymentMethods[2].totalSpending").value(24_500))
                .andExpect(jsonPath("$.data.paymentMethods[2].transactionCount").value(3))
                .andExpect(jsonPath("$.data.paymentMethods[2].sharePercent").value(4.4));

        mockMvc.perform(get("/api/v1/spending/payment-methods").param("yearMonth", lastMonth.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentMethods[1].paymentMethod").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.paymentMethods[1].totalSpending").value(5_000));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/spending/monthly-summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/spending/categories")).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void insertTx(
            String id, Long ownerId, LocalDate date, String type, long amount, Long categoryId,
            String spendingType, String paymentMethod, boolean hidden
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO `transaction` (
                    transaction_id, user_id, account_id, card_id, category_id,
                    transaction_date, transaction_time, transaction_type, amount, balance_after,
                    merchant_name, payment_method, spending_type, summary, memo,
                    hidden, source_type, created_at, updated_at
                ) VALUES (?, ?, NULL, NULL, ?, ?, NULL, ?, ?, NULL, NULL, ?, ?, ?, NULL, ?, 'SEED', ?, NULL)
                """,
                id, ownerId, categoryId, date, type, amount, paymentMethod, spendingType, id, hidden, SEEDED_AT
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
