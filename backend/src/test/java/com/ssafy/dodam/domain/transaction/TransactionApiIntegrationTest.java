package com.ssafy.dodam.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
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
 * S15P21D105-504 거래 조회·메모·카테고리·숨김 API. 실제 회원가입·로그인으로 발급한 토큰으로 호출한다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = "/sql/transaction-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class TransactionApiIntegrationTest {

    private static final String EMAIL = "transaction@example.com";
    private static final String OTHER_EMAIL = "other-transaction@example.com";
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

    @BeforeEach
    void setUp() throws Exception {
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        jdbcTemplate.update("DELETE FROM `transaction`");
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

        // 오늘 기준 기본 조회기간(3개월) 안: 4건 노출 + 1건 숨김
        insertTransaction("TX-001", userId, today, LocalTime.of(12, 30), "EXPENSE", 12000,
                2L, "CARD", "VARIABLE", "스타벅스 강남", "카페", null, false, "SSAFY", 11L, null);
        insertTransaction("TX-002", userId, today, LocalTime.of(9, 5), "EXPENSE", 1500,
                4L, "CARD", "VARIABLE", "서울교통공사", "지하철", "출근", false, "SSAFY", null, 1L);
        insertTransaction("TX-003", userId, today.minusDays(10), LocalTime.of(18, 0), "INCOME", 3000000,
                19L, "ACCOUNT", "NON_SPENDING", "도담컴퍼니", "급여", null, false, "SSAFY", 11L, null);
        insertTransaction("TX-004", userId, today.minusDays(40), null, "EXPENSE", 45000,
                null, "ACCOUNT", null, "분류 전 거래", "미분류", null, false, "MANUAL", null, null);
        insertTransaction("TX-005", userId, today.minusDays(5), LocalTime.of(20, 0), "EXPENSE", 99000,
                1L, "CARD", "VARIABLE", "숨긴 거래", "숨김", null, true, "SEED", null, 1L);
        // 기본 조회기간 밖
        insertTransaction("TX-OLD", userId, today.minusMonths(5), LocalTime.of(10, 0), "EXPENSE", 7000,
                1L, "CARD", "VARIABLE", "오래된 거래", "과거", null, false, "SEED", null, 1L);
        // 타인 거래
        insertTransaction("TX-OTHER", otherUserId, today, LocalTime.of(13, 0), "EXPENSE", 3000,
                1L, "CARD", "VARIABLE", "타인 가맹점", "타인", null, false, "SEED", null, 2L);
    }

    // ---------- 전체 거래 조회 ----------

    @Test
    void listsVisibleTransactionsInDefaultPeriodNewestFirst() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(4)))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.last").value(true))
                .andExpect(jsonPath("$.data.content[0].transactionId").value("TX-001"))
                .andExpect(jsonPath("$.data.content[0].categoryCode").value("CAFE"))
                .andExpect(jsonPath("$.data.content[0].categoryName").value("카페"))
                .andExpect(jsonPath("$.data.content[0].accountId").value(11))
                .andExpect(jsonPath("$.data.content[0].hidden").value(false))
                .andExpect(jsonPath("$.data.content[1].transactionId").value("TX-002"))
                .andExpect(jsonPath("$.data.content[2].transactionId").value("TX-003"))
                .andExpect(jsonPath("$.data.content[2].transactionType").value("INCOME"))
                .andExpect(jsonPath("$.data.content[3].transactionId").value("TX-004"))
                .andExpect(jsonPath("$.data.content[3].categoryId").value(nullValue()))
                .andExpect(jsonPath("$.data.content[3].transactionTime").value(nullValue()));
    }

    @Test
    void listsOnlyHiddenTransactionsWhenHiddenIsTrue() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").param("hidden", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].transactionId").value("TX-005"))
                .andExpect(jsonPath("$.data.content[0].hidden").value(true));
    }

    @Test
    void filtersByCategoryCodeIgnoringCase() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").param("category", "cafe")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].transactionId").value("TX-001"));
    }

    @Test
    void filtersByPaymentMethod() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").param("paymentMethod", "account")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].transactionId").value("TX-003"))
                .andExpect(jsonPath("$.data.content[1].transactionId").value("TX-004"));
    }

    @Test
    void filtersByExplicitPeriodAndPages() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                        .param("startDate", today.minusMonths(6).toString())
                        .param("endDate", today.toString())
                        .param("page", "1")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].transactionId").value("TX-003"))
                .andExpect(jsonPath("$.data.content[1].transactionId").value("TX-004"));
    }

    @Test
    void keepsExactTotalsOnPartialAndOutOfRangePages() throws Exception {
        for (int page : new int[] {2, 3}) {
            mockMvc.perform(get("/api/v1/transactions")
                            .param("startDate", today.minusMonths(6).toString())
                            .param("endDate", today.toString())
                            .param("page", String.valueOf(page))
                            .param("size", "2")
                            .header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(5))
                    .andExpect(jsonPath("$.data.totalPages").value(3))
                    .andExpect(jsonPath("$.data.content", hasSize(page == 2 ? 1 : 0)));
        }
    }

    @Test
    void rejectsUnknownCategoryCode() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").param("category", "NOPE")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 카테고리 코드입니다."));
    }

    @Test
    void rejectsStartDateAfterEndDate() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                        .param("startDate", today.toString())
                        .param("endDate", today.minusDays(1).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsPageSizeOverPolicy() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").param("size", "101")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 거래 상세 ----------

    @Test
    void getsOwnedTransactionDetail() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/TX-002").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionId").value("TX-002"))
                .andExpect(jsonPath("$.data.cardId").value(1))
                .andExpect(jsonPath("$.data.categoryId").value(4))
                .andExpect(jsonPath("$.data.categoryCode").value("PUBLIC_TRANSPORT"))
                .andExpect(jsonPath("$.data.amount").value(1500))
                .andExpect(jsonPath("$.data.paymentMethod").value("CARD"))
                .andExpect(jsonPath("$.data.spendingType").value("VARIABLE"))
                .andExpect(jsonPath("$.data.summary").value("지하철"))
                .andExpect(jsonPath("$.data.memo").value("출근"))
                .andExpect(jsonPath("$.data.sourceType").value("SSAFY"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-09-01T09:00:00"))
                .andExpect(jsonPath("$.data.updatedAt").value(nullValue()));
    }

    @Test
    void getsHiddenTransactionDetail() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/TX-005").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hidden").value(true));
    }

    @Test
    void hidesOtherUsersTransactionAsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/TX-OTHER").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("거래를 찾을 수 없습니다."));
    }

    @Test
    void returnsNotFoundForUnknownTransaction() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/TX-MISSING").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void rejectsMalformedTransactionId() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/TX 001").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---------- 카테고리 목록 ----------

    @Test
    void listsActiveCategoriesOnly() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/transactions/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(8)))
                .andExpect(jsonPath("$.data[0].categoryId").value(1))
                .andExpect(jsonPath("$.data[0].code").value("FOOD"))
                .andExpect(jsonPath("$.data[0].defaultSpendingType").value("VARIABLE"))
                .andReturn();

        JsonNode codes = readData(result);
        assertThat(codes.findValuesAsText("code")).doesNotContain("LEGACY");
    }

    // ---------- 소비 카테고리 수정 ----------

    @Test
    void changesCategoryAndDerivesSpendingType() throws Exception {
        mockMvc.perform(patch("/api/v1/transactions/TX-001/category")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("categoryId", 12))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryId").value(12))
                .andExpect(jsonPath("$.data.categoryCode").value("HOUSING"))
                .andExpect(jsonPath("$.data.categoryName").value("주거·관리비"))
                .andExpect(jsonPath("$.data.spendingType").value("FIXED"))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        assertThat(jdbcTemplate.queryForMap(
                "SELECT category_id, spending_type, updated_at FROM `transaction` WHERE transaction_id = 'TX-001'"))
                .containsEntry("category_id", 12L)
                .containsEntry("spending_type", "FIXED")
                .hasEntrySatisfying("updated_at", value -> assertThat(value).isNotNull());
    }

    @Test
    void keepsSpendingTypeWhenCategoryHasNoDefault() throws Exception {
        mockMvc.perform(patch("/api/v1/transactions/TX-001/category")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("categoryId", 950))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryCode").value("NO_DEFAULT"))
                .andExpect(jsonPath("$.data.spendingType").value("VARIABLE"));
    }

    @Test
    void assignsCategoryToUncategorizedTransaction() throws Exception {
        mockMvc.perform(patch("/api/v1/transactions/TX-004/category")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("categoryId", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryCode").value("FOOD"))
                .andExpect(jsonPath("$.data.spendingType").value("VARIABLE"));
    }

    @Test
    void ignoresDuplicateCategoryRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/transactions/TX-001/category")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("categoryId", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryCode").value("CAFE"))
                .andExpect(jsonPath("$.data.updatedAt").value(nullValue()));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT updated_at FROM `transaction` WHERE transaction_id = 'TX-001'", Object.class))
                .isNull();
    }

    @Test
    void rejectsUnknownOrInactiveCategory() throws Exception {
        mockMvc.perform(patch("/api/v1/transactions/TX-001/category")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("categoryId", 999))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 카테고리입니다."));

        mockMvc.perform(patch("/api/v1/transactions/TX-001/category")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("categoryId", 900))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsMissingCategoryId() throws Exception {
        mockMvc.perform(patch("/api/v1/transactions/TX-001/category")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));
    }

    @Test
    void cannotChangeCategoryOfOtherUsersTransaction() throws Exception {
        mockMvc.perform(patch("/api/v1/transactions/TX-OTHER/category")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("categoryId", 1))))
                .andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT category_id FROM `transaction` WHERE transaction_id = 'TX-OTHER'", Long.class))
                .isEqualTo(1L);
    }

    // ---------- 거래 메모 수정 ----------

    @Test
    void changesMemo() throws Exception {
        mockMvc.perform(patch("/api/v1/transactions/TX-001/memo")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("memo", "  팀 회식 커피\n2차  "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memo").value("팀 회식 커피\n2차"))
                .andExpect(jsonPath("$.data.categoryCode").value("CAFE"))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT memo FROM `transaction` WHERE transaction_id = 'TX-001'", String.class))
                .isEqualTo("팀 회식 커피\n2차");
    }

    @Test
    void clearsMemoWithBlankOrNull() throws Exception {
        mockMvc.perform(patch("/api/v1/transactions/TX-002/memo")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("memo", "   "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memo").value(nullValue()));

        mockMvc.perform(patch("/api/v1/transactions/TX-002/memo")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memo").value(nullValue()));
    }

    @Test
    void ignoresDuplicateMemoRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/transactions/TX-002/memo")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("memo", "출근"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memo").value("출근"))
                .andExpect(jsonPath("$.data.updatedAt").value(nullValue()));
    }

    @Test
    void rejectsMemoOverMaxLength() throws Exception {
        String tooLong = String.join("", Collections.nCopies(501, "가"));
        mockMvc.perform(patch("/api/v1/transactions/TX-001/memo")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("memo", tooLong))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("메모는 500자 이하여야 합니다."));
    }

    @Test
    void rejectsMemoWithControlCharacters() throws Exception {
        mockMvc.perform(patch("/api/v1/transactions/TX-001/memo")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("memo", "메모" + (char) 1 + "끝"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("메모에 사용할 수 없는 문자가 포함되어 있습니다."));
    }

    @Test
    void cannotChangeMemoOfOtherUsersTransaction() throws Exception {
        mockMvc.perform(patch("/api/v1/transactions/TX-OTHER/memo")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("memo", "침입"))))
                .andExpect(status().isNotFound());
    }

    // ---------- 거래내역 숨김 상태 변경 ----------

    @Test
    void hidesAndRevealsTransaction() throws Exception {
        mockMvc.perform(patch("/api/v1/transactions/TX-001/visibility")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("hidden", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hidden").value(true))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/transactions").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(3)))
                .andExpect(jsonPath("$.data.content[0].transactionId").value("TX-002"));

        mockMvc.perform(patch("/api/v1/transactions/TX-001/visibility")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("hidden", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hidden").value(false));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT hidden FROM `transaction` WHERE transaction_id = 'TX-001'", Boolean.class))
                .isFalse();
    }

    @Test
    void ignoresDuplicateVisibilityRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/transactions/TX-005/visibility")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("hidden", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hidden").value(true))
                .andExpect(jsonPath("$.data.updatedAt").value(nullValue()));
    }

    @Test
    void rejectsMissingHiddenFlag() throws Exception {
        mockMvc.perform(patch("/api/v1/transactions/TX-001/visibility")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("hidden"));
    }

    @Test
    void cannotChangeVisibilityOfOtherUsersTransaction() throws Exception {
        mockMvc.perform(patch("/api/v1/transactions/TX-OTHER/visibility")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("hidden", true))))
                .andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT hidden FROM `transaction` WHERE transaction_id = 'TX-OTHER'", Boolean.class))
                .isFalse();
    }

    // ---------- helpers ----------

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
        return "Bearer " + readData(result).path("accessToken").asText();
    }

    private JsonNode readData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void insertTransaction(
            String transactionId,
            Long ownerId,
            LocalDate transactionDate,
            LocalTime transactionTime,
            String transactionType,
            long amount,
            Long categoryId,
            String paymentMethod,
            String spendingType,
            String merchantName,
            String summary,
            String memo,
            boolean hidden,
            String sourceType,
            Long accountId,
            Long cardId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO `transaction` (
                    transaction_id, user_id, account_id, card_id, category_id,
                    transaction_date, transaction_time, transaction_type, amount, balance_after,
                    merchant_name, payment_method, spending_type, summary, memo,
                    hidden, source_type, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
                """,
                transactionId,
                ownerId,
                accountId,
                cardId,
                categoryId,
                transactionDate,
                transactionTime,
                transactionType,
                amount,
                merchantName,
                paymentMethod,
                spendingType,
                summary,
                memo,
                hidden,
                sourceType,
                SEEDED_AT
        );
    }
}
