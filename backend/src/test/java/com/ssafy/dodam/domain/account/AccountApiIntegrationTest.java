package com.ssafy.dodam.domain.account;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.ssafy.dodam.global.crypto.FieldCryptor;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyFinanceClient;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyMember;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyTransferEntry;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyAccountBalance;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyApiException;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyUnavailableException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
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
 * S15P21D105-501 계좌 목록·상세·잔액 API. 실제 회원가입·로그인으로 발급한 토큰으로 호출한다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = "/sql/account-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AccountApiIntegrationTest {

    private static final String EMAIL = "account@example.com";
    private static final String OTHER_EMAIL = "other-account@example.com";
    private static final String PASSWORD = "password123";

    private static final String MAIN_ACCOUNT_NO = "1234567890123456";
    private static final String SALARY_ACCOUNT_NO = "1101234567890123";
    private static final String MINUS_ACCOUNT_NO = "2222222233333333";
    private static final String SAVINGS_ACCOUNT_NO = "3333333344444444";
    private static final String CLOSED_ACCOUNT_NO = "4444444455555555";
    private static final String OTHER_USER_ACCOUNT_NO = "1100000000000021";
    private static final String UNKNOWN_ACCOUNT_NO = "9999999999999999";

    private static final LocalDateTime SEEDED_AT = LocalDateTime.of(2026, 9, 7, 9, 0);
    private static final LocalDateTime SYNCED_AT = LocalDateTime.of(2026, 9, 10, 18, 30);

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

    @Autowired
    private FieldCryptor fieldCryptor;

    @MockitoBean
    private SsafyFinanceClient ssafyFinanceClient;

    private Long userId;
    private Long otherUserId;
    private String bearer;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM `transaction`");
        jdbcTemplate.update("""
                MERGE INTO transaction_category (category_id, code, name, default_spending_type, active)
                KEY (category_id) VALUES (20, 'SAVING', '저축·투자', 'NON_SPENDING', TRUE)
                """);
        jdbcTemplate.update("DELETE FROM account");
        authSessionRepository.deleteAll();
        financeConnectionRepository.deleteAll();
        userSettingRepository.deleteAll();
        financialProfileRepository.deleteAll();
        userRepository.deleteAll();

        // 사용자마다 다른 userKey를 돌려준다. 같은 키면 finance_connection의 userKey 해시 유니크 제약에 걸린다.
        given(ssafyFinanceClient.createMember(anyString()))
                .willAnswer(invocation -> new SsafyMember(
                        invocation.getArgument(0), "회원", "00100", "user-key-" + invocation.getArgument(0)));

        signup(EMAIL, "도담");
        signup(OTHER_EMAIL, "타인");
        userId = userRepository.findByEmailAndDeletedAtIsNull(EMAIL).orElseThrow().getId();
        otherUserId = userRepository.findByEmailAndDeletedAtIsNull(OTHER_EMAIL).orElseThrow().getId();
        bearer = login(EMAIL);

        insertAccount(11L, userId, "004", "KB국민은행", MAIN_ACCOUNT_NO, "123456-**-**3456", "KB 주거래통장",
                "DEMAND_DEPOSIT", "ACTIVE", 1_850_000, LocalDate.of(2026, 9, 10), "SSAFY", SYNCED_AT);
        insertAccount(12L, userId, "088", "신한은행", SALARY_ACCOUNT_NO, "110-123-***123", "신한 급여통장",
                "DEMAND_DEPOSIT", "ACTIVE", 320_000, LocalDate.of(2026, 9, 5), "SSAFY", SYNCED_AT);
        insertAccount(13L, userId, "004", "KB국민은행", MINUS_ACCOUNT_NO, "222222-**-**3333", "KB 마이너스통장",
                "MINUS", "ACTIVE", -2_400_000, LocalDate.of(2026, 8, 31), "SEED", SEEDED_AT);
        insertAccount(14L, userId, "020", "우리은행", SAVINGS_ACCOUNT_NO, "333333-**-**4444", "우리 적금",
                "SAVINGS", "INACTIVE", 600_000, null, "SEED", SEEDED_AT);
        insertAccount(15L, userId, "004", "KB국민은행", CLOSED_ACCOUNT_NO, "444444-**-**5555", "해지한 통장",
                "DEMAND_DEPOSIT", "CLOSED", 0, LocalDate.of(2025, 12, 1), "SEED", SEEDED_AT);
        insertAccount(21L, otherUserId, "004", "KB국민은행", OTHER_USER_ACCOUNT_NO, "110000-**-**0021", "타인 통장",
                "DEMAND_DEPOSIT", "ACTIVE", 5_000_000, LocalDate.of(2026, 9, 9), "SEED", SEEDED_AT);
    }

    // ---------- 계좌 전체 조회 ----------

    @Test
    void listsOwnedAccountsOrderedByStatusBankAndName() throws Exception {
        mockMvc.perform(get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(5)))
                .andExpect(jsonPath("$.data[0].accountId").value(13))
                .andExpect(jsonPath("$.data[0].accountName").value("KB 마이너스통장"))
                .andExpect(jsonPath("$.data[0].accountType").value("MINUS"))
                .andExpect(jsonPath("$.data[0].balance").value(-2400000))
                .andExpect(jsonPath("$.data[1].accountId").value(11))
                .andExpect(jsonPath("$.data[1].accountNoMasked").value("123456-**-**3456"))
                .andExpect(jsonPath("$.data[1].bankCode").value("004"))
                .andExpect(jsonPath("$.data[1].currency").value("KRW"))
                .andExpect(jsonPath("$.data[1].sourceType").value("SSAFY"))
                .andExpect(jsonPath("$.data[1].lastTransactionDate").value("2026-09-10"))
                .andExpect(jsonPath("$.data[2].accountId").value(12))
                .andExpect(jsonPath("$.data[2].bankName").value("신한은행"))
                .andExpect(jsonPath("$.data[3].accountId").value(14))
                .andExpect(jsonPath("$.data[3].status").value("INACTIVE"))
                .andExpect(jsonPath("$.data[3].lastTransactionDate").value(nullValue()))
                .andExpect(jsonPath("$.data[4].accountId").value(15))
                .andExpect(jsonPath("$.data[4].status").value("CLOSED"))
                .andExpect(jsonPath("$.data[*].accountNo").doesNotExist())
                .andExpect(jsonPath("$.data[*].accountNoHash").doesNotExist());
    }

    @Test
    void filtersByBankCode() throws Exception {
        mockMvc.perform(get("/api/v1/accounts").param("bankCode", "088")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].accountId").value(12));
    }

    @Test
    void filtersByAccountTypeIgnoringCase() throws Exception {
        mockMvc.perform(get("/api/v1/accounts").param("accountType", "demand_deposit")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].accountId").value(11))
                .andExpect(jsonPath("$.data[1].accountId").value(12))
                .andExpect(jsonPath("$.data[2].accountId").value(15));
    }

    @Test
    void combinesFiltersAndReturnsEmptyListWhenNothingMatches() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                        .param("bankCode", "088")
                        .param("accountType", "MINUS")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void rejectsUnknownAccountType() throws Exception {
        mockMvc.perform(get("/api/v1/accounts").param("accountType", "CHECKING")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("지원하지 않는 계좌 유형입니다."));
    }

    @Test
    void rejectsMalformedBankCode() throws Exception {
        mockMvc.perform(get("/api/v1/accounts").param("bankCode", "00-4")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 계좌 상세 조회 ----------

    @Test
    void getsOwnedAccountDetailByAccountNo() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value(11))
                .andExpect(jsonPath("$.data.bankCode").value("004"))
                .andExpect(jsonPath("$.data.bankName").value("KB국민은행"))
                .andExpect(jsonPath("$.data.accountNoMasked").value("123456-**-**3456"))
                .andExpect(jsonPath("$.data.accountName").value("KB 주거래통장"))
                .andExpect(jsonPath("$.data.accountType").value("DEMAND_DEPOSIT"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.balance").value(1850000))
                .andExpect(jsonPath("$.data.currency").value("KRW"))
                .andExpect(jsonPath("$.data.lastTransactionDate").value("2026-09-10"))
                .andExpect(jsonPath("$.data.sourceType").value("SSAFY"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-09-10T18:30:00"))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-09-10T18:30:00"))
                .andExpect(jsonPath("$.data.accountNo").doesNotExist());
    }

    @Test
    void getsClosedAccountDetail() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + CLOSED_ACCOUNT_NO).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.balance").value(0));
    }

    @Test
    void hidesOtherUsersAccountAsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + OTHER_USER_ACCOUNT_NO).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("계좌를 찾을 수 없습니다."));
    }

    @Test
    void returnsNotFoundForUnknownAccount() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + UNKNOWN_ACCOUNT_NO).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void rejectsMalformedAccountNo() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/1234-5678").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---------- 계좌 잔액 조회 ----------

    @Test
    void getsBalanceOfOwnedAccount() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/balance")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value(11))
                .andExpect(jsonPath("$.data.bankName").value("KB국민은행"))
                .andExpect(jsonPath("$.data.accountNoMasked").value("123456-**-**3456"))
                .andExpect(jsonPath("$.data.accountName").value("KB 주거래통장"))
                .andExpect(jsonPath("$.data.accountType").value("DEMAND_DEPOSIT"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.balance").value(1850000))
                .andExpect(jsonPath("$.data.currency").value("KRW"))
                .andExpect(jsonPath("$.data.lastTransactionDate").value("2026-09-10"))
                .andExpect(jsonPath("$.data.balanceAsOf").value("2026-09-10T18:30:00"))
                .andExpect(jsonPath("$.data.bankCode").doesNotExist())
                .andExpect(jsonPath("$.data.accountNo").doesNotExist());
    }

    @Test
    void returnsNegativeBalanceForMinusAccount() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + MINUS_ACCOUNT_NO + "/balance")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountType").value("MINUS"))
                .andExpect(jsonPath("$.data.balance").value(-2400000));
    }

    @Test
    void hidesOtherUsersBalanceAsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + OTHER_USER_ACCOUNT_NO + "/balance")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // ---------- helpers ----------

    @Test
    void transferMovesBalancesAndWritesTwoSavingTransactionsOnlyOnce() throws Exception {
        prepareTransferAccounts();
        given(ssafyFinanceClient.transfer(anyString(), anyString(), anyString(), anyLong(), anyString(), anyString()))
                .willReturn(transferEntries(100_000));

        mockMvc.perform(post("/api/v1/accounts/transfer").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(transferJson(100_000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.withdrawalBalance").value(1_750_000))
                .andExpect(jsonPath("$.data.depositBalance").value(420_000));
        mockMvc.perform(post("/api/v1/accounts/transfer").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(transferJson(100_000)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSFER_DUPLICATE_REQUEST"));

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForList(
                "SELECT transaction_type, spending_type, category_id FROM `transaction` WHERE transaction_id IN ('TEST-OUT', 'TEST-IN') ORDER BY transaction_id"))
                .extracting(row -> row.get("TRANSACTION_TYPE") + ":" + row.get("SPENDING_TYPE"))
                .containsExactly("INCOME:NON_SPENDING", "EXPENSE:NON_SPENDING");
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `transaction` WHERE transaction_id IN ('TEST-OUT', 'TEST-IN')", Integer.class))
                .isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM `transaction` t JOIN transaction_category c ON c.category_id = t.category_id
                WHERE t.transaction_id IN ('TEST-OUT', 'TEST-IN') AND c.code = 'SAVING'
                  AND t.spending_type = 'NON_SPENDING' AND t.source_type = 'SSAFY'
                """, Integer.class)).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT last_transaction_date FROM account WHERE account_id = 11", LocalDate.class))
                .isEqualTo(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")));
        verify(ssafyFinanceClient, times(1)).transfer(anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void financeErrorsNeverChangeLocalBalancesOrTransactions() throws Exception {
        prepareTransferAccounts();
        given(ssafyFinanceClient.transfer(anyString(), anyString(), anyString(), anyLong(), anyString(), anyString()))
                .willThrow(new SsafyApiException("A1014", "잔액 부족"),
                        new SsafyApiException("A1016", "한도 초과"),
                        new SsafyApiException("A1017", "일일 한도"),
                        new SsafyApiException("A1003", "계좌 오류"),
                        new SsafyUnavailableException("통신 실패", null));
        String[] codes = {"TRANSFER_INSUFFICIENT_BALANCE", "TRANSFER_LIMIT_EXCEEDED",
                "TRANSFER_LIMIT_EXCEEDED", "FINANCE_API_ERROR", "FINANCE_API_UNAVAILABLE"};
        int[] statuses = {409, 409, 409, 502, 503};
        for (int i = 0; i < codes.length; i++) {
            mockMvc.perform(post("/api/v1/accounts/transfer").header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON).content(transferJson(100_000)))
                    .andExpect(status().is(statuses[i]))
                    .andExpect(jsonPath("$.code").value(codes[i]));
            org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                    "SELECT balance FROM account WHERE account_id = 11", Long.class)).isEqualTo(1_850_000L);
            org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                    "SELECT balance FROM account WHERE account_id = 12", Long.class)).isEqualTo(320_000L);
            org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `transaction` WHERE transaction_id IN ('TEST-OUT', 'TEST-IN')", Integer.class)).isZero();
        }
    }

    @Test
    void transferRejectsInvalidAccountsAndUnauthenticatedRequest() throws Exception {
        prepareTransferAccounts();
        mockMvc.perform(post("/api/v1/accounts/transfer").contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(100_000)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/accounts/transfer").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("withdrawalAccountId", 11, "depositAccountId", 11, "amount", 100_000))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/accounts/transfer").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(transferJson(0)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/accounts/transfer").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("withdrawalAccountId", 11, "depositAccountId", 21, "amount", 100_000))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/accounts/transfer").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("withdrawalAccountId", 11, "depositAccountId", 14, "amount", 100_000))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/accounts/transfer").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("withdrawalAccountId", 11, "depositAccountId", 15, "amount", 100_000))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferRaisesLinkedGoalProgressAndAdvancesForecastWithoutChangingCompletedMonthPace() throws Exception {
        prepareTransferAccounts();
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        jdbcTemplate.update("""
                INSERT INTO `transaction` (transaction_id, user_id, account_id, transaction_date,
                    transaction_type, amount, source_type, created_at)
                VALUES ('PACE-BEFORE', ?, 12, ?, 'INCOME', 100000, 'SEED', ?)
                """, userId, today.minusMonths(1).withDayOfMonth(15), LocalDateTime.now());
        MvcResult created = mockMvc.perform(post("/api/v1/goals").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "목표 통장", "type", "SAVING", "targetAmount", 1_000_000,
                                "targetDate", today.plusYears(1).toString()))))
                .andExpect(status().isCreated()).andReturn();
        long goalId = objectMapper.readTree(created.getResponse().getContentAsString()).path("data").path("goalId").asLong();
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("accountIds", List.of(12L)))))
                .andExpect(status().isOk());
        MvcResult before = mockMvc.perform(get("/api/v1/goals/" + goalId + "/forecast")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andReturn();
        var beforeData = objectMapper.readTree(before.getResponse().getContentAsString()).path("data");

        List<SsafyTransferEntry> reversed = transferEntries(100_000);
        given(ssafyFinanceClient.transfer(anyString(), anyString(), anyString(), anyLong(), anyString(), anyString()))
                .willReturn(List.of(reversed.get(1), reversed.get(0)));
        mockMvc.perform(post("/api/v1/accounts/transfer").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(transferJson(100_000)))
                .andExpect(status().isOk());
        MvcResult after = mockMvc.perform(get("/api/v1/goals/" + goalId + "/forecast")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andReturn();
        var afterData = objectMapper.readTree(after.getResponse().getContentAsString()).path("data");
        org.assertj.core.api.Assertions.assertThat(afterData.path("currentAmount").asLong())
                .isEqualTo(beforeData.path("currentAmount").asLong() + 100_000);
        org.assertj.core.api.Assertions.assertThat(afterData.path("averageMonthlySaving").asLong())
                .isEqualTo(beforeData.path("averageMonthlySaving").asLong());
        org.assertj.core.api.Assertions.assertThat(LocalDate.parse(afterData.path("expectedAchievementDate").asText()))
                .isBefore(LocalDate.parse(beforeData.path("expectedAchievementDate").asText()));
    }

    @Test
    void nextAccountReadReconcilesBalancesAfterLocalSaveFailure() throws Exception {
        prepareTransferAccounts();
        jdbcTemplate.update("DELETE FROM transaction_category WHERE code = 'SAVING'");
        given(ssafyFinanceClient.transfer(anyString(), anyString(), anyString(), anyLong(), anyString(), anyString()))
                .willReturn(transferEntries(100_000));
        mockMvc.perform(post("/api/v1/accounts/transfer").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(transferJson(100_000)))
                .andExpect(status().is5xxServerError());
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_id = 11", Long.class)).isEqualTo(1_850_000L);
        given(ssafyFinanceClient.inquireAccountBalance(anyString(), anyString()))
                .willAnswer(invocation -> new SsafyAccountBalance(invocation.getArgument(1),
                        MAIN_ACCOUNT_NO.equals(invocation.getArgument(1)) ? 1_750_000L : 420_000L));
        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/balance")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(1_750_000));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_id = 12", Long.class)).isEqualTo(420_000L);
    }

    private void prepareTransferAccounts() {
        jdbcTemplate.update("UPDATE account SET account_no_encrypted = ? WHERE account_id = 11",
                fieldCryptor.encrypt(MAIN_ACCOUNT_NO));
        jdbcTemplate.update("UPDATE account SET account_no_encrypted = ? WHERE account_id = 12",
                fieldCryptor.encrypt(SALARY_ACCOUNT_NO));
    }

    private String transferJson(long amount) throws Exception {
        return json(Map.of("withdrawalAccountId", 11, "depositAccountId", 12, "amount", amount));
    }

    private static List<SsafyTransferEntry> transferEntries(long amount) {
        return List.of(
                new SsafyTransferEntry("TEST-OUT", "20260923", "103000", "2", amount, 1_750_000L, "출금"),
                new SsafyTransferEntry("TEST-IN", "20260923", "103000", "1", amount, 420_000L, "입금"));
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

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void insertAccount(
            Long accountId,
            Long ownerId,
            String bankCode,
            String bankName,
            String accountNo,
            String accountNoMasked,
            String accountName,
            String accountType,
            String status,
            long balance,
            LocalDate lastTransactionDate,
            String sourceType,
            LocalDateTime timestamp
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO account (
                    account_id, user_id, bank_code, bank_name, account_no_encrypted, account_no_hash,
                    account_no_masked, account_name, account_type, status, balance, last_transaction_date,
                    currency, source_type, created_at, updated_at
                ) VALUES (?, ?, ?, ?, X'00', ?, ?, ?, ?, ?, ?, ?, 'KRW', ?, ?, ?)
                """,
                accountId,
                ownerId,
                bankCode,
                bankName,
                sensitiveNumberHasher.hash(accountNo),
                accountNoMasked,
                accountName,
                accountType,
                status,
                balance,
                lastTransactionDate,
                sourceType,
                timestamp,
                timestamp
        );
    }
}
