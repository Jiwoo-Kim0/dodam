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
import java.time.LocalTime;
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
 * S15P21D105-502 계좌 거래내역·거래 단건 조회. 원장(`transaction`)에 직접 넣은 데이터를 읽는다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = "/sql/account-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AccountTransactionApiIntegrationTest {

    private static final String EMAIL = "account-tx@example.com";
    private static final String OTHER_EMAIL = "other-account-tx@example.com";
    private static final String PASSWORD = "password123";

    private static final String MAIN_ACCOUNT_NO = "1234567890123456";
    private static final String OTHER_ACCOUNT_NO = "1101234567890123";
    private static final String OTHER_USER_ACCOUNT_NO = "1100000000000021";
    private static final Long MAIN_ACCOUNT_ID = 11L;
    private static final Long OTHER_ACCOUNT_ID = 12L;
    private static final Long OTHER_USER_ACCOUNT_ID = 21L;

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

    @BeforeEach
    void setUp() throws Exception {
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));

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

        insertAccount(MAIN_ACCOUNT_ID, userId, MAIN_ACCOUNT_NO, "KB 주거래통장");
        insertAccount(OTHER_ACCOUNT_ID, userId, OTHER_ACCOUNT_NO, "신한 급여통장");
        insertAccount(OTHER_USER_ACCOUNT_ID, otherUserId, OTHER_USER_ACCOUNT_NO, "타인 통장");

        // 기본 조회기간(3개월) 안: 3건 노출 + 1건 숨김
        insertTransaction("TX-A11-001", userId, MAIN_ACCOUNT_ID, today, LocalTime.of(12, 30), "EXPENSE", 12000,
                1_838_000L, "스타벅스", "카페", 1L, false);
        insertTransaction("TX-A11-002", userId, MAIN_ACCOUNT_ID, today, LocalTime.of(9, 5), "INCOME", 3_000_000,
                1_850_000L, "급여", "9월 급여", 19L, false);
        insertTransaction("TX-A11-003", userId, MAIN_ACCOUNT_ID, today.minusDays(20), null, "TRANSFER", 200_000,
                null, "적금 이체", null, 18L, false);
        insertTransaction("TX-A11-HID", userId, MAIN_ACCOUNT_ID, today.minusDays(2), LocalTime.of(20, 0), "EXPENSE", 99_000,
                null, "숨긴 거래", null, null, true);
        // 기본 조회기간 밖
        insertTransaction("TX-A11-OLD", userId, MAIN_ACCOUNT_ID, today.minusMonths(5), LocalTime.of(10, 0), "EXPENSE", 7000,
                null, "오래된 거래", null, 1L, false);
        // 같은 사용자의 다른 계좌
        insertTransaction("TX-A12-001", userId, OTHER_ACCOUNT_ID, today, LocalTime.of(13, 0), "EXPENSE", 5000,
                null, "다른 계좌 거래", null, 1L, false);
        // 타인 계좌
        insertTransaction("TX-A21-001", otherUserId, OTHER_USER_ACCOUNT_ID, today, LocalTime.of(13, 0), "EXPENSE", 3000,
                null, "타인 거래", null, 1L, false);
    }

    // ---------- 계좌 거래내역 조회 ----------

    @Test
    void listsVisibleTransactionsOfAccountInDefaultPeriodNewestFirst() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(3)))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.last").value(true))
                .andExpect(jsonPath("$.data.content[0].transactionId").value("TX-A11-001"))
                .andExpect(jsonPath("$.data.content[0].transactionType").value("EXPENSE"))
                .andExpect(jsonPath("$.data.content[0].amount").value(12000))
                .andExpect(jsonPath("$.data.content[0].balanceAfter").value(1838000))
                .andExpect(jsonPath("$.data.content[0].summary").value("스타벅스"))
                .andExpect(jsonPath("$.data.content[0].memo").value("카페"))
                .andExpect(jsonPath("$.data.content[0].categoryCode").value("FOOD"))
                .andExpect(jsonPath("$.data.content[0].categoryName").value("식비"))
                .andExpect(jsonPath("$.data.content[0].hidden").value(false))
                .andExpect(jsonPath("$.data.content[1].transactionId").value("TX-A11-002"))
                .andExpect(jsonPath("$.data.content[1].transactionType").value("INCOME"))
                .andExpect(jsonPath("$.data.content[2].transactionId").value("TX-A11-003"))
                .andExpect(jsonPath("$.data.content[2].transactionTime").value(nullValue()))
                .andExpect(jsonPath("$.data.content[2].balanceAfter").value(nullValue()))
                .andExpect(jsonPath("$.data.content[2].categoryCode").value("TRANSFER"));
    }

    @Test
    void filtersByExplicitPeriodAndPages() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/transactions")
                        .param("startDate", today.minusMonths(6).toString())
                        .param("endDate", today.toString())
                        .param("page", "1")
                        .param("size", "3")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].transactionId").value("TX-A11-OLD"));
    }

    @Test
    void rejectsInvalidPeriodAndPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/transactions")
                        .param("startDate", today.toString())
                        .param("endDate", today.minusDays(1).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/transactions")
                        .param("size", "101")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void hidesOtherUsersAccountTransactionsAsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + OTHER_USER_ACCOUNT_NO + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("계좌를 찾을 수 없습니다."));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/transactions"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 거래 단건 조회 ----------

    @Test
    void getsSingleTransactionIncludingHiddenOne() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/transactions/TX-A11-002")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionId").value("TX-A11-002"))
                .andExpect(jsonPath("$.data.transactionType").value("INCOME"))
                .andExpect(jsonPath("$.data.amount").value(3000000))
                .andExpect(jsonPath("$.data.balanceAfter").value(1850000))
                .andExpect(jsonPath("$.data.summary").value("급여"))
                .andExpect(jsonPath("$.data.memo").value("9월 급여"))
                .andExpect(jsonPath("$.data.categoryCode").value("INCOME"))
                .andExpect(jsonPath("$.data.hidden").value(false));

        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/transactions/TX-A11-HID")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hidden").value(true));
    }

    @Test
    void transactionOfAnotherAccountIsNotFoundEvenWhenOwned() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/transactions/TX-A12-001")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("거래를 찾을 수 없습니다."));
    }

    @Test
    void transactionOfOtherUsersAccountIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + OTHER_USER_ACCOUNT_NO + "/transactions/TX-A21-001")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("계좌를 찾을 수 없습니다."));
    }

    @Test
    void rejectsMalformedTransactionId() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/transactions/TX A11")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---------- helpers ----------

    private void insertAccount(Long accountId, Long ownerId, String accountNo, String accountName) {
        jdbcTemplate.update(
                """
                INSERT INTO account (
                    account_id, user_id, bank_code, bank_name, account_no_encrypted, account_no_hash,
                    account_no_masked, account_name, account_type, status, balance, last_transaction_date,
                    currency, source_type, created_at, updated_at
                ) VALUES (?, ?, '004', 'KB국민은행', X'00', ?, '123456-**-**3456', ?,
                          'DEMAND_DEPOSIT', 'ACTIVE', 1850000, NULL, 'KRW', 'SEED', ?, ?)
                """,
                accountId, ownerId, sensitiveNumberHasher.hash(accountNo), accountName, SEEDED_AT, SEEDED_AT
        );
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void insertTransaction(
            String transactionId,
            Long ownerId,
            Long accountId,
            LocalDate transactionDate,
            LocalTime transactionTime,
            String transactionType,
            long amount,
            Long balanceAfter,
            String summary,
            String memo,
            Long categoryId,
            boolean hidden
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO `transaction` (
                    transaction_id, user_id, account_id, card_id, category_id,
                    transaction_date, transaction_time, transaction_type, amount, balance_after,
                    merchant_name, payment_method, spending_type, summary, memo,
                    hidden, source_type, created_at, updated_at
                ) VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, NULL, 'ACCOUNT', NULL, ?, ?, ?, 'SEED', ?, NULL)
                """,
                transactionId, ownerId, accountId, categoryId, transactionDate, transactionTime, transactionType,
                amount, balanceAfter, summary, memo, hidden, SEEDED_AT
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
