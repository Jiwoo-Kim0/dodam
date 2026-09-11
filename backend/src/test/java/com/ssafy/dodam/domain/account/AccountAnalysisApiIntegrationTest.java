package com.ssafy.dodam.domain.account;

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
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
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
 * S15P21D105-503 계좌 요약·현금성 자산·휴면성 계좌·월별 입출금 요약·입출금 추이.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = "/sql/account-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AccountAnalysisApiIntegrationTest {

    private static final String EMAIL = "account-analysis@example.com";
    private static final String OTHER_EMAIL = "other-account-analysis@example.com";
    private static final String PASSWORD = "password123";

    private static final String MAIN_ACCOUNT_NO = "1234567890123456";
    private static final String OTHER_USER_ACCOUNT_NO = "1100000000000021";
    private static final Long MAIN_ACCOUNT_ID = 11L;

    private static final LocalDateTime SEEDED_AT = LocalDateTime.of(2026, 9, 7, 9, 0);

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
    private Long otherUserId;
    private String bearer;
    private LocalDate today;
    private YearMonth thisMonth;

    @BeforeEach
    void setUp() throws Exception {
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        thisMonth = YearMonth.from(today);

        jdbcTemplate.update("DELETE FROM `transaction`");
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
        otherUserId = userRepository.findByEmailAndDeletedAtIsNull(OTHER_EMAIL).orElseThrow().getId();
        bearer = login(EMAIL);

        // 수시입출금 3(ACTIVE 2, INACTIVE 1) + 예금 1 + 적금 1 + 마이너스 1 + CLOSED 1
        insertAccount(MAIN_ACCOUNT_ID, userId, "004", "KB국민은행", MAIN_ACCOUNT_NO, "KB 주거래통장",
                "DEMAND_DEPOSIT", "ACTIVE", 1_850_000, today.minusDays(1), SEEDED_AT);
        insertAccount(12L, userId, "088", "신한은행", "1101234567890123", "신한 급여통장",
                "DEMAND_DEPOSIT", "ACTIVE", 150_000, today.minusMonths(8), SEEDED_AT);
        insertAccount(13L, userId, "020", "우리은행", "2020202020202020", "우리 옛통장",
                "DEMAND_DEPOSIT", "INACTIVE", 30_000, null, LocalDateTime.of(2025, 1, 10, 9, 0));
        insertAccount(14L, userId, "004", "KB국민은행", "3030303030303030", "KB 정기예금",
                "DEPOSIT", "ACTIVE", 10_000_000, null, SEEDED_AT);
        insertAccount(15L, userId, "004", "KB국민은행", "4040404040404040", "KB 적금",
                "SAVINGS", "ACTIVE", 3_000_000, null, SEEDED_AT);
        insertAccount(16L, userId, "004", "KB국민은행", "5050505050505050", "KB 마이너스통장",
                "MINUS", "ACTIVE", -800_000, today.minusMonths(9), SEEDED_AT);
        insertAccount(17L, userId, "004", "KB국민은행", "6060606060606060", "해지한 통장",
                "DEMAND_DEPOSIT", "CLOSED", 999_999, today.minusYears(2), SEEDED_AT);
        insertAccount(21L, otherUserId, "004", "KB국민은행", OTHER_USER_ACCOUNT_NO, "타인 통장",
                "DEMAND_DEPOSIT", "ACTIVE", 77_000_000, today.minusYears(1), SEEDED_AT);

        // 이번 달 거래: 입금 2건, 출금 2건, 이체 1건, 숨김 1건 / 지난달: 입금 1건 / 3개월 전: 출금 1건
        insertTransaction("TX-M-001", userId, MAIN_ACCOUNT_ID, thisMonth.atDay(1), "INCOME", 3_000_000, false);
        insertTransaction("TX-M-002", userId, MAIN_ACCOUNT_ID, thisMonth.atDay(1), "INCOME", 50_000, false);
        insertTransaction("TX-M-003", userId, MAIN_ACCOUNT_ID, thisMonth.atDay(1), "EXPENSE", 120_000, false);
        insertTransaction("TX-M-004", userId, MAIN_ACCOUNT_ID, thisMonth.atDay(1), "EXPENSE", 30_000, false);
        insertTransaction("TX-M-005", userId, MAIN_ACCOUNT_ID, thisMonth.atDay(1), "TRANSFER", 200_000, false);
        insertTransaction("TX-M-HID", userId, MAIN_ACCOUNT_ID, thisMonth.atDay(1), "EXPENSE", 999_000, true);
        insertTransaction("TX-P-001", userId, MAIN_ACCOUNT_ID, thisMonth.minusMonths(1).atDay(15), "INCOME", 3_000_000, false);
        insertTransaction("TX-Q-001", userId, MAIN_ACCOUNT_ID, thisMonth.minusMonths(3).atDay(20), "EXPENSE", 45_000, false);
        // 다른 계좌·타인 거래는 집계에 섞이지 않아야 한다
        insertTransaction("TX-O-001", userId, 12L, thisMonth.atDay(2), "EXPENSE", 5_000_000, false);
        insertTransaction("TX-U-001", otherUserId, 21L, thisMonth.atDay(2), "INCOME", 9_000_000, false);
    }

    // ---------- 계좌 요약 ----------

    @Test
    void summarizesOpenAccountsByTypeAndStatus() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/summary").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(6))
                .andExpect(jsonPath("$.data.activeCount").value(5))
                .andExpect(jsonPath("$.data.inactiveCount").value(1))
                .andExpect(jsonPath("$.data.closedCount").value(1))
                .andExpect(jsonPath("$.data.demandDepositCount").value(3))
                .andExpect(jsonPath("$.data.demandDepositBalance").value(2_030_000))
                .andExpect(jsonPath("$.data.depositCount").value(1))
                .andExpect(jsonPath("$.data.depositBalance").value(10_000_000))
                .andExpect(jsonPath("$.data.savingsCount").value(1))
                .andExpect(jsonPath("$.data.savingsBalance").value(3_000_000))
                .andExpect(jsonPath("$.data.minusAccountCount").value(1))
                .andExpect(jsonPath("$.data.minusUsedAmount").value(800_000))
                .andExpect(jsonPath("$.data.totalAssets").value(15_030_000))
                .andExpect(jsonPath("$.data.netAssets").value(14_230_000));
    }

    @Test
    void summaryOfUserWithoutAccountsIsAllZero() throws Exception {
        jdbcTemplate.update("DELETE FROM account WHERE user_id = ?", userId);
        mockMvc.perform(get("/api/v1/accounts/summary").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0))
                .andExpect(jsonPath("$.data.totalAssets").value(0))
                .andExpect(jsonPath("$.data.netAssets").value(0));
    }

    // ---------- 현금성 자산 합계 ----------

    @Test
    void sumsDemandDepositBalancesWithShares() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/balance-summary").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCash").value(2_030_000))
                .andExpect(jsonPath("$.data.accountCount").value(3))
                .andExpect(jsonPath("$.data.accounts", hasSize(3)))
                .andExpect(jsonPath("$.data.accounts[0].accountId").value(11))
                .andExpect(jsonPath("$.data.accounts[0].balance").value(1_850_000))
                .andExpect(jsonPath("$.data.accounts[0].sharePercent").value(91.1))
                .andExpect(jsonPath("$.data.accounts[1].accountId").value(12))
                .andExpect(jsonPath("$.data.accounts[1].sharePercent").value(7.4))
                .andExpect(jsonPath("$.data.accounts[2].accountId").value(13))
                .andExpect(jsonPath("$.data.accounts[2].status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.accounts[2].sharePercent").value(1.5))
                .andExpect(jsonPath("$.data.accounts[*].accountNo").doesNotExist());
    }

    // ---------- 휴면성 계좌 ----------

    @Test
    void detectsInactiveDemandDepositAccountsWithDefaultSixMonths() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/inactive").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.criteria.inactiveMonths").value(6))
                .andExpect(jsonPath("$.data.criteria.maxBalance").value(nullValue()))
                .andExpect(jsonPath("$.data.criteria.asOfDate").value(today.toString()))
                .andExpect(jsonPath("$.data.count").value(2))
                .andExpect(jsonPath("$.data.totalBalance").value(180_000))
                // 거래일이 없는 계좌는 적재일(2025-01-10)을 마지막 활동일로 본다 → 가장 오래됨
                .andExpect(jsonPath("$.data.accounts[0].accountId").value(13))
                .andExpect(jsonPath("$.data.accounts[0].lastActivityDate").value("2025-01-10"))
                .andExpect(jsonPath("$.data.accounts[0].status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.accounts[1].accountId").value(12))
                .andExpect(jsonPath("$.data.accounts[1].lastActivityDate").value(today.minusMonths(8).toString()))
                .andExpect(jsonPath("$.data.accounts[1].inactiveMonths").value(8))
                .andExpect(jsonPath("$.data.accounts[1].balance").value(150_000));
    }

    @Test
    void inactiveCriteriaCanBeTightenedByMonthsAndBalance() throws Exception {
        // 12개월 기준이면 8개월 전 거래 계좌는 빠진다
        mockMvc.perform(get("/api/v1/accounts/inactive").param("inactiveMonths", "12")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.accounts[0].accountId").value(13));

        // 소액 상한 100,000원이면 150,000원 계좌는 빠진다
        mockMvc.perform(get("/api/v1/accounts/inactive").param("maxBalance", "100000")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.criteria.maxBalance").value(100000))
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.accounts[0].accountId").value(13));

        // 1개월 기준이면 어제 거래한 주거래통장은 여전히 빠지고, 잔액 0·CLOSED·마이너스통장은 어떤 기준에서도 잡히지 않는다
        mockMvc.perform(get("/api/v1/accounts/inactive").param("inactiveMonths", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2));
    }

    @Test
    void rejectsInvalidInactiveParameters() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/inactive").param("inactiveMonths", "0")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/accounts/inactive").param("maxBalance", "-1")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---------- 월별 입출금 요약 ----------

    @Test
    void summarizesCurrentMonthFlowsExcludingHiddenAndOtherAccounts() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/monthly-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value(11))
                .andExpect(jsonPath("$.data.accountNoMasked").value("123456-**-**3456"))
                .andExpect(jsonPath("$.data.yearMonth").value(thisMonth.toString()))
                .andExpect(jsonPath("$.data.incomeTotal").value(3_050_000))
                .andExpect(jsonPath("$.data.incomeCount").value(2))
                .andExpect(jsonPath("$.data.expenseTotal").value(150_000))
                .andExpect(jsonPath("$.data.expenseCount").value(2))
                .andExpect(jsonPath("$.data.transferTotal").value(200_000))
                .andExpect(jsonPath("$.data.transferCount").value(1))
                .andExpect(jsonPath("$.data.netChange").value(2_900_000))
                .andExpect(jsonPath("$.data.transactionCount").value(5));
    }

    @Test
    void monthlySummaryOfMonthWithoutTransactionsIsZero() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/monthly-summary")
                        .param("yearMonth", thisMonth.minusMonths(2).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value(thisMonth.minusMonths(2).toString()))
                .andExpect(jsonPath("$.data.incomeTotal").value(0))
                .andExpect(jsonPath("$.data.expenseTotal").value(0))
                .andExpect(jsonPath("$.data.transactionCount").value(0));
    }

    @Test
    void rejectsFutureOrMalformedYearMonth() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/monthly-summary")
                        .param("yearMonth", thisMonth.plusMonths(1).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/monthly-summary")
                        .param("yearMonth", "2026-13")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void monthlySummaryOfOtherUsersAccountIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + OTHER_USER_ACCOUNT_NO + "/monthly-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // ---------- 입출금 추이 ----------

    @Test
    void trendFillsEveryMonthInDefaultThreeMonthPeriod() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/trend")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startMonth").value(thisMonth.minusMonths(2).toString()))
                .andExpect(jsonPath("$.data.endMonth").value(thisMonth.toString()))
                .andExpect(jsonPath("$.data.months", hasSize(3)))
                .andExpect(jsonPath("$.data.months[0].yearMonth").value(thisMonth.minusMonths(2).toString()))
                .andExpect(jsonPath("$.data.months[0].transactionCount").value(0))
                .andExpect(jsonPath("$.data.months[1].yearMonth").value(thisMonth.minusMonths(1).toString()))
                .andExpect(jsonPath("$.data.months[1].incomeTotal").value(3_000_000))
                .andExpect(jsonPath("$.data.months[1].netChange").value(3_000_000))
                .andExpect(jsonPath("$.data.months[2].yearMonth").value(thisMonth.toString()))
                .andExpect(jsonPath("$.data.months[2].expenseTotal").value(150_000))
                .andExpect(jsonPath("$.data.months[2].transferTotal").value(200_000))
                .andExpect(jsonPath("$.data.totalIncome").value(6_050_000))
                .andExpect(jsonPath("$.data.totalExpense").value(150_000))
                .andExpect(jsonPath("$.data.totalNetChange").value(5_900_000));
    }

    @Test
    void trendHonoursExplicitPeriod() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/trend")
                        .param("startMonth", thisMonth.minusMonths(3).toString())
                        .param("endMonth", thisMonth.minusMonths(3).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.months", hasSize(1)))
                .andExpect(jsonPath("$.data.months[0].expenseTotal").value(45_000))
                .andExpect(jsonPath("$.data.totalNetChange").value(-45_000));
    }

    @Test
    void rejectsInvalidTrendPeriod() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/trend")
                        .param("startMonth", thisMonth.toString())
                        .param("endMonth", thisMonth.minusMonths(1).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/trend")
                        .param("startMonth", thisMonth.minusMonths(12).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("조회 기간은 최대 12개월까지 가능합니다."));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/accounts/inactive")).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void insertAccount(
            Long accountId, Long ownerId, String bankCode, String bankName, String accountNo, String accountName,
            String accountType, String status, long balance, LocalDate lastTransactionDate, LocalDateTime createdAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO account (
                    account_id, user_id, bank_code, bank_name, account_no_encrypted, account_no_hash,
                    account_no_masked, account_name, account_type, status, balance, last_transaction_date,
                    currency, source_type, created_at, updated_at
                ) VALUES (?, ?, ?, ?, X'00', ?, '123456-**-**3456', ?, ?, ?, ?, ?, 'KRW', 'SEED', ?, ?)
                """,
                accountId, ownerId, bankCode, bankName, sensitiveNumberHasher.hash(accountNo), accountName,
                accountType, status, balance, lastTransactionDate, createdAt, createdAt
        );
    }

    private void insertTransaction(
            String transactionId, Long ownerId, Long accountId, LocalDate date, String type, long amount, boolean hidden
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO `transaction` (
                    transaction_id, user_id, account_id, card_id, category_id,
                    transaction_date, transaction_time, transaction_type, amount, balance_after,
                    merchant_name, payment_method, spending_type, summary, memo,
                    hidden, source_type, created_at, updated_at
                ) VALUES (?, ?, ?, NULL, NULL, ?, NULL, ?, ?, NULL, NULL, 'ACCOUNT', NULL, ?, NULL, ?, 'SEED', ?, NULL)
                """,
                transactionId, ownerId, accountId, date, type, amount, transactionId, hidden, SEEDED_AT
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
