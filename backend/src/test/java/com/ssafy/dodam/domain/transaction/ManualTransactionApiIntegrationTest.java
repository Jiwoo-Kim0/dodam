package com.ssafy.dodam.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyFinanceClient;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyMember;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
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
 * S15P21D105-505 수동 거래 등록·수정·삭제. 실제 회원가입·로그인으로 발급한 토큰으로 호출한다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = "/sql/transaction-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ManualTransactionApiIntegrationTest {

    private static final String EMAIL = "manual-tx@example.com";
    private static final String OTHER_EMAIL = "other-manual-tx@example.com";
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
    private String otherBearer;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));

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
        otherBearer = login(OTHER_EMAIL);

        // 금융망 출처 거래 1건: 수정·삭제 불가 검증용
        jdbcTemplate.update(
                """
                INSERT INTO `transaction` (
                    transaction_id, user_id, account_id, card_id, category_id,
                    transaction_date, transaction_time, transaction_type, amount, balance_after,
                    merchant_name, payment_method, spending_type, summary, memo,
                    hidden, source_type, created_at, updated_at
                ) VALUES ('1001', ?, 11, NULL, 2, ?, NULL, 'EXPENSE', 12000, 1488000,
                          NULL, 'ACCOUNT', 'VARIABLE', '스타벅스', NULL, FALSE, 'SSAFY', ?, NULL)
                """,
                userId, today, SEEDED_AT
        );
    }

    // ---------- 등록 ----------

    @Test
    void createsManualTransactionWithCategoryDefaultSpendingType() throws Exception {
        Map<String, Object> body = body(today.minusDays(1), "expense", 8500L, "  동네 분식  ", 12L, "cash", " 점심 ");
        body.put("transactionTime", "13:05");

        MvcResult result = mockMvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transactionId").value(startsWith("MN-")))
                .andExpect(jsonPath("$.data.transactionDate").value(today.minusDays(1).toString()))
                .andExpect(jsonPath("$.data.transactionTime").value("13:05:00"))
                .andExpect(jsonPath("$.data.transactionType").value("EXPENSE"))
                .andExpect(jsonPath("$.data.amount").value(8500))
                .andExpect(jsonPath("$.data.balanceAfter").value(nullValue()))
                .andExpect(jsonPath("$.data.merchantName").value("동네 분식"))
                .andExpect(jsonPath("$.data.categoryId").value(12))
                .andExpect(jsonPath("$.data.categoryCode").value("HOUSING"))
                .andExpect(jsonPath("$.data.spendingType").value("FIXED"))
                .andExpect(jsonPath("$.data.paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.data.memo").value("점심"))
                .andExpect(jsonPath("$.data.hidden").value(false))
                .andExpect(jsonPath("$.data.sourceType").value("MANUAL"))
                .andExpect(jsonPath("$.data.accountId").value(nullValue()))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.updatedAt").value(nullValue()))
                .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("transactionId").asText();
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT source_type, account_id, category_id, spending_type, transaction_time FROM `transaction` WHERE transaction_id = ?", id);
        assertThat(row.get("source_type")).isEqualTo("MANUAL");
        assertThat(row.get("account_id")).isNull();
        assertThat(row.get("category_id")).isEqualTo(12L);
        assertThat(row.get("spending_type")).isEqualTo("FIXED");
        assertThat(String.valueOf(row.get("transaction_time"))).startsWith("13:05");

        // 등록 직후 목록·상세에 보인다(JDBC reader가 시각을 그대로 읽는다)
        mockMvc.perform(get("/api/v1/transactions/" + id).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merchantName").value("동네 분식"))
                .andExpect(jsonPath("$.data.transactionTime").value("13:05:00"));
        mockMvc.perform(get("/api/v1/transactions").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.data.content", hasSize(2)));
    }

    @Test
    void createsMinimalManualTransactionWithoutOptionalFields() throws Exception {
        Map<String, Object> minimal = new HashMap<>();
        minimal.put("transactionDate", today.toString());
        minimal.put("transactionType", "INCOME");
        minimal.put("amount", 3_000_000);

        mockMvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(minimal)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.transactionType").value("INCOME"))
                .andExpect(jsonPath("$.data.transactionTime").value(nullValue()))
                .andExpect(jsonPath("$.data.merchantName").value(nullValue()))
                .andExpect(jsonPath("$.data.categoryId").value(nullValue()))
                .andExpect(jsonPath("$.data.spendingType").value(nullValue()))
                .andExpect(jsonPath("$.data.paymentMethod").value(nullValue()))
                .andExpect(jsonPath("$.data.memo").value(nullValue()));
    }

    @Test
    void acceptsTimeWithSecondsAndRejectsUnparseableTime() throws Exception {
        Map<String, Object> withSeconds = body(today, "EXPENSE", 1000L, null, null, null, null);
        withSeconds.put("transactionTime", "23:59:30");
        mockMvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(withSeconds)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.transactionTime").value("23:59:30"));

        Map<String, Object> invalid = body(today, "EXPENSE", 1000L, null, null, null, null);
        invalid.put("transactionTime", "25:00");
        mockMvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `transaction` WHERE source_type = 'MANUAL'", Long.class)).isEqualTo(1L);
    }

    @Test
    void listsSameDayTransactionsByTimeDescendingWithNullTimeLast() throws Exception {
        String morning = createManual(today, "EXPENSE", 1000L, "아침", null, null, null, "08:30");
        String noTime = createManual(today, "EXPENSE", 2000L, "시각 없음", null, null, null, null);
        String evening = createManual(today, "EXPENSE", 3000L, "저녁", null, null, null, "19:45");

        // fixture의 SSAFY 거래(1001, 오늘, 시각 null)도 같은 날이라 시각 없는 두 건은 ID 내림차순으로 이어진다
        mockMvc.perform(get("/api/v1/transactions").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(4)))
                .andExpect(jsonPath("$.data.content[0].transactionId").value(evening))
                .andExpect(jsonPath("$.data.content[0].transactionTime").value("19:45:00"))
                .andExpect(jsonPath("$.data.content[1].transactionId").value(morning))
                .andExpect(jsonPath("$.data.content[1].transactionTime").value("08:30:00"))
                .andExpect(jsonPath("$.data.content[2].transactionTime").value(nullValue()))
                .andExpect(jsonPath("$.data.content[3].transactionTime").value(nullValue()));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT transaction_time FROM `transaction` WHERE transaction_id = ?", Object.class, noTime)).isNull();
    }

    @Test
    void rejectsMissingRequiredFieldsAndNonPositiveAmount() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors", hasSize(3)));

        mockMvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(today, "EXPENSE", 0L, null, null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
    }

    @Test
    void rejectsFutureDateUnknownTypeAndUnknownCategory() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(today.plusDays(1), "EXPENSE", 1000L, null, null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("거래일은 오늘 이후일 수 없습니다."));

        mockMvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(today, "REFUND", 1000L, null, null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지원하지 않는 거래 유형입니다."));

        mockMvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(today, "EXPENSE", 1000L, null, 900L, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("존재하지 않는 카테고리입니다."));

        mockMvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(today, "EXPENSE", 1000L, null, null, "신용 카드", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("paymentMethod"));

        mockMvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(today, "EXPENSE", 1000L, null, null, null, "가".repeat(501)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("메모는 500자 이하여야 합니다."));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `transaction` WHERE source_type = 'MANUAL'", Long.class)).isZero();
    }

    // ---------- 수정 ----------

    @Test
    void updatesOnlyProvidedFieldsAndClearsWithBlank() throws Exception {
        String id = createManual(today.minusDays(3), "EXPENSE", 20_000L, "카페", 2L, "CARD", "원래 메모", "12:00");

        mockMvc.perform(put("/api/v1/transactions/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 25_000, "memo", "", "categoryId", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionId").value(id))
                .andExpect(jsonPath("$.data.amount").value(25000))
                .andExpect(jsonPath("$.data.memo").value(nullValue()))
                .andExpect(jsonPath("$.data.categoryCode").value("FOOD"))
                .andExpect(jsonPath("$.data.transactionDate").value(today.minusDays(3).toString()))
                .andExpect(jsonPath("$.data.transactionTime").value("12:00:00"))
                .andExpect(jsonPath("$.data.merchantName").value("카페"))
                .andExpect(jsonPath("$.data.paymentMethod").value("CARD"))
                .andExpect(jsonPath("$.data.sourceType").value("MANUAL"))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        mockMvc.perform(put("/api/v1/transactions/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("transactionDate", today.toString(), "transactionTime", "00:22",
                                "transactionType", "income", "merchantName", "  ", "paymentMethod", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionDate").value(today.toString()))
                .andExpect(jsonPath("$.data.transactionTime").value("00:22:00"))
                .andExpect(jsonPath("$.data.transactionType").value("INCOME"))
                .andExpect(jsonPath("$.data.merchantName").value(nullValue()))
                .andExpect(jsonPath("$.data.paymentMethod").value(nullValue()))
                .andExpect(jsonPath("$.data.categoryCode").value("FOOD"));

        // 시각만 보내도 "빈 Body"가 아니고, 시각이 없던 거래에 시각을 채울 수 있다
        String noTime = createManual(today, "EXPENSE", 5000L, null, null, null, null);
        mockMvc.perform(put("/api/v1/transactions/" + noTime)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("transactionTime", "09:15"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionTime").value("09:15:00"))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
        mockMvc.perform(get("/api/v1/transactions/" + noTime).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.data.transactionTime").value("09:15:00"));
    }

    @Test
    void duplicateUpdateKeepsUpdatedAt() throws Exception {
        String id = createManual(today, "EXPENSE", 20_000L, "카페", 2L, "CARD", "메모", "12:00");

        mockMvc.perform(put("/api/v1/transactions/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 20_000, "merchantName", "카페", "categoryId", 2,
                                "transactionTime", "12:00:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionTime").value("12:00:00"))
                .andExpect(jsonPath("$.data.updatedAt").value(nullValue()));
    }

    @Test
    void rejectsEmptyUpdateBody() throws Exception {
        String id = createManual(today, "EXPENSE", 20_000L, null, null, null, null);

        mockMvc.perform(put("/api/v1/transactions/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_FIELDS_TO_UPDATE"));
    }

    @Test
    void rejectsInvalidUpdateValues() throws Exception {
        String id = createManual(today, "EXPENSE", 20_000L, null, null, null, null);

        mockMvc.perform(put("/api/v1/transactions/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", -5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(put("/api/v1/transactions/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("transactionDate", today.plusDays(2).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("거래일은 오늘 이후일 수 없습니다."));

        // 검증에 실패하면 어떤 필드도 바뀌지 않는다
        mockMvc.perform(get("/api/v1/transactions/" + id).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.data.amount").value(20000))
                .andExpect(jsonPath("$.data.transactionDate").value(today.toString()));
    }

    @Test
    void cannotUpdateFinanceSourcedTransaction() throws Exception {
        mockMvc.perform(put("/api/v1/transactions/1001")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_MANUAL"))
                .andExpect(jsonPath("$.message").value("수동으로 등록한 거래만 수정·삭제할 수 있습니다."));

        assertThat(jdbcTemplate.queryForObject("SELECT amount FROM `transaction` WHERE transaction_id = '1001'", Long.class))
                .isEqualTo(12_000L);
    }

    @Test
    void cannotUpdateOtherUsersTransaction() throws Exception {
        String id = createManual(today, "EXPENSE", 20_000L, null, null, null, null);

        mockMvc.perform(put("/api/v1/transactions/" + id)
                        .header(HttpHeaders.AUTHORIZATION, otherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // ---------- 삭제 ----------

    @Test
    void deletesManualTransaction() throws Exception {
        String id = createManual(today, "EXPENSE", 20_000L, "카페", 2L, "CARD", null);

        mockMvc.perform(delete("/api/v1/transactions/" + id).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/v1/transactions/" + id).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/transactions/" + id).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotDeleteFinanceSourcedOrOtherUsersTransaction() throws Exception {
        mockMvc.perform(delete("/api/v1/transactions/1001").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_MANUAL"));

        String id = createManual(today, "EXPENSE", 20_000L, null, null, null, null);
        mockMvc.perform(delete("/api/v1/transactions/" + id).header(HttpHeaders.AUTHORIZATION, otherBearer))
                .andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `transaction`", Long.class)).isEqualTo(2L);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body(today, "EXPENSE", 1000L, null, null, null, null))))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private String createManual(
            LocalDate date, String type, Long amount, String merchant, Long categoryId, String paymentMethod, String memo
    ) throws Exception {
        return createManual(date, type, amount, merchant, categoryId, paymentMethod, memo, null);
    }

    private String createManual(
            LocalDate date, String type, Long amount, String merchant, Long categoryId, String paymentMethod, String memo,
            String time
    ) throws Exception {
        Map<String, Object> body = body(date, type, amount, merchant, categoryId, paymentMethod, memo);
        if (time != null) {
            body.put("transactionTime", time);
        }
        MvcResult result = mockMvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("transactionId").asText();
    }

    private static Map<String, Object> body(
            LocalDate date, String type, Long amount, String merchant, Long categoryId, String paymentMethod, String memo
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("transactionDate", date.toString());
        body.put("transactionType", type);
        body.put("amount", amount);
        if (merchant != null) {
            body.put("merchantName", merchant);
        }
        if (categoryId != null) {
            body.put("categoryId", categoryId);
        }
        if (paymentMethod != null) {
            body.put("paymentMethod", paymentMethod);
        }
        if (memo != null) {
            body.put("memo", memo);
        }
        return body;
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
