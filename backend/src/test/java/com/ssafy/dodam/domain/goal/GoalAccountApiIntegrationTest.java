package com.ssafy.dodam.domain.goal;

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
 * S15P21D105-509 목표 연결계좌 조회·변경.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = "/sql/goal-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class GoalAccountApiIntegrationTest {

    private static final String EMAIL = "goal-account@example.com";
    private static final String OTHER_EMAIL = "other-goal-account@example.com";
    private static final String PASSWORD = "password123";

    private static final String MAIN_NO = "1234567890123456";
    private static final String SAVINGS_NO = "3333333344444444";
    private static final String DEPOSIT_NO = "3030303030303030";
    private static final String MINUS_NO = "2222222233333333";
    private static final String CLOSED_NO = "4444444455555555";
    private static final String OTHER_USER_NO = "1100000000000021";
    private static final String UNKNOWN_NO = "9999999999999999";
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
    private String otherBearer;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));

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
        otherUserId = userRepository.findByEmailAndDeletedAtIsNull(OTHER_EMAIL).orElseThrow().getId();
        bearer = login(EMAIL);
        otherBearer = login(OTHER_EMAIL);

        insertAccount(11L, userId, MAIN_NO, "KB 주거래통장", "DEMAND_DEPOSIT", "ACTIVE", 1_850_000);
        insertAccount(12L, userId, SAVINGS_NO, "우리 적금", "SAVINGS", "INACTIVE", 600_000);
        insertAccount(13L, userId, DEPOSIT_NO, "KB 정기예금", "DEPOSIT", "ACTIVE", 10_000_000);
        insertAccount(14L, userId, MINUS_NO, "KB 마이너스통장", "MINUS", "ACTIVE", -800_000);
        insertAccount(15L, userId, CLOSED_NO, "해지한 통장", "DEMAND_DEPOSIT", "CLOSED", 0);
        insertAccount(21L, otherUserId, OTHER_USER_NO, "타인 통장", "DEMAND_DEPOSIT", "ACTIVE", 5_000_000);
    }

    @Test
    void linksAccountsAndUpdatesGoalCurrentAmount() throws Exception {
        long goalId = createSaving("유럽 여행", 3_000_000);

        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountNos", List.of(SAVINGS_NO, MAIN_NO, MAIN_NO)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goalId").value(goalId))
                .andExpect(jsonPath("$.data.linkedCount").value(2))
                .andExpect(jsonPath("$.data.totalBalance").value(2_450_000))
                .andExpect(jsonPath("$.data.accounts", hasSize(2)))
                .andExpect(jsonPath("$.data.accounts[0].accountId").value(11))
                .andExpect(jsonPath("$.data.accounts[0].accountNoMasked").value("123456-**-**3456"))
                .andExpect(jsonPath("$.data.accounts[0].accountType").value("DEMAND_DEPOSIT"))
                .andExpect(jsonPath("$.data.accounts[0].balance").value(1_850_000))
                .andExpect(jsonPath("$.data.accounts[0].contributionAmount").value(nullValue()))
                .andExpect(jsonPath("$.data.accounts[1].accountId").value(12))
                .andExpect(jsonPath("$.data.accounts[1].status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.accounts[*].accountNo").doesNotExist());

        // 목표의 모인 금액·진행률이 갱신된다
        mockMvc.perform(get("/api/v1/goals/" + goalId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentAmount").value(2_450_000))
                .andExpect(jsonPath("$.data.remainingAmount").value(550_000))
                .andExpect(jsonPath("$.data.progressRatePercent").value(81.7))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT user_id, account_type FROM goal_account WHERE goal_id = ? AND account_id = 12", goalId);
        assertThat(row.get("user_id")).isEqualTo(userId);
        assertThat(row.get("account_type")).isEqualTo("SAVINGS");
    }

    @Test
    void listsLinkedAccountsWithPaging() throws Exception {
        long goalId = createSaving("목표", 20_000_000);
        link(goalId, List.of(MAIN_NO, SAVINGS_NO, DEPOSIT_NO));

        mockMvc.perform(get("/api/v1/goals/" + goalId + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linkedCount").value(3))
                .andExpect(jsonPath("$.data.totalBalance").value(12_450_000))
                .andExpect(jsonPath("$.data.accounts", hasSize(3)))
                .andExpect(jsonPath("$.data.accounts[2].accountId").value(13))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.last").value(true));

        mockMvc.perform(get("/api/v1/goals/" + goalId + "/accounts").param("page", "1").param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linkedCount").value(3))
                .andExpect(jsonPath("$.data.accounts", hasSize(1)))
                .andExpect(jsonPath("$.data.accounts[0].accountId").value(13))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    void emptyListForGoalWithoutAccountsAndForSpendingGoal() throws Exception {
        long saving = createSaving("빈 목표", 1_000_000);
        long spending = createSpending("줄이기", 300_000);

        mockMvc.perform(get("/api/v1/goals/" + saving + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linkedCount").value(0))
                .andExpect(jsonPath("$.data.totalBalance").value(0))
                .andExpect(jsonPath("$.data.accounts", hasSize(0)))
                .andExpect(jsonPath("$.data.totalPages").value(0));
        mockMvc.perform(get("/api/v1/goals/" + spending + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accounts", hasSize(0)));
    }

    @Test
    void replacesLinkSetAndUnlinksAllWithEmptyArray() throws Exception {
        long goalId = createSaving("목표", 20_000_000);
        link(goalId, List.of(MAIN_NO, SAVINGS_NO));

        // 하나 빼고 하나 추가
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountNos", List.of(MAIN_NO, DEPOSIT_NO)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linkedCount").value(2))
                .andExpect(jsonPath("$.data.totalBalance").value(11_850_000))
                .andExpect(jsonPath("$.data.accounts[0].accountId").value(11))
                .andExpect(jsonPath("$.data.accounts[1].accountId").value(13));
        assertThat(jdbcTemplate.queryForList("SELECT account_id FROM goal_account WHERE goal_id = ? ORDER BY account_id",
                Long.class, goalId)).containsExactly(11L, 13L);

        // 빈 배열 → 모두 해제, 모인 금액 0
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountNos", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linkedCount").value(0))
                .andExpect(jsonPath("$.data.totalBalance").value(0));
        assertThat(jdbcTemplate.queryForObject("SELECT current_amount FROM goal WHERE goal_id = ?", Long.class, goalId)).isZero();
    }

    @Test
    void sameSetIsIdempotentAndRefreshesBalance() throws Exception {
        long goalId = createSaving("목표", 5_000_000);
        link(goalId, List.of(MAIN_NO));
        String updatedAt = jdbcTemplate.queryForObject("SELECT updated_at FROM goal WHERE goal_id = ?", String.class, goalId);

        // 같은 집합이면 연결 행은 그대로, 잔액이 안 바뀌면 updated_at도 그대로
        link(goalId, List.of(MAIN_NO));
        assertThat(jdbcTemplate.queryForObject("SELECT updated_at FROM goal WHERE goal_id = ?", String.class, goalId))
                .isEqualTo(updatedAt);

        // 잔액이 바뀐 뒤 같은 집합으로 다시 PUT하면 모인 금액만 갱신된다
        jdbcTemplate.update("UPDATE account SET balance = 2000000 WHERE account_id = 11");
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountNos", List.of(MAIN_NO)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBalance").value(2_000_000));
        assertThat(jdbcTemplate.queryForObject("SELECT current_amount FROM goal WHERE goal_id = ?", Long.class, goalId))
                .isEqualTo(2_000_000L);
    }

    @Test
    void rejectsUnlinkableAccountsWithoutPartialChange() throws Exception {
        long goalId = createSaving("목표", 5_000_000);
        link(goalId, List.of(MAIN_NO));

        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountNos", List.of(SAVINGS_NO, MINUS_NO)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입출금·예금·적금 계좌만 목표에 연결할 수 있습니다."));
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountNos", List.of(CLOSED_NO)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("해지된 계좌는 목표에 연결할 수 없습니다."));
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountNos", List.of(OTHER_USER_NO)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("계좌를 찾을 수 없습니다."));
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountNos", List.of(UNKNOWN_NO)))))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountNos", List.of("12-34")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 기존 연결은 그대로
        assertThat(jdbcTemplate.queryForList("SELECT account_id FROM goal_account WHERE goal_id = ?", Long.class, goalId))
                .containsExactly(11L);
    }

    @Test
    void rejectsAccountLinkedToAnotherLiveGoalButTakesOverFromDeletedGoal() throws Exception {
        long first = createSaving("첫 목표", 5_000_000);
        long second = createSaving("둘째 목표", 5_000_000);
        link(first, List.of(MAIN_NO));

        mockMvc.perform(put("/api/v1/goals/" + second + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountNos", List.of(MAIN_NO)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_LINKED_TO_GOAL"));

        // 첫 목표를 지우면(soft delete) 둘째 목표가 그 계좌를 이어받는다
        mockMvc.perform(delete("/api/v1/goals/" + first).header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/goals/" + second + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountNos", List.of(MAIN_NO)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linkedCount").value(1));
        assertThat(jdbcTemplate.queryForList("SELECT goal_id FROM goal_account WHERE account_id = 11", Long.class))
                .containsExactly(second);
    }

    @Test
    void rejectsSpendingGoalTerminalGoalAndOtherUsersGoal() throws Exception {
        long spending = createSpending("줄이기", 300_000);
        mockMvc.perform(put("/api/v1/goals/" + spending + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountNos", List.of(MAIN_NO)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("줄이기 목표에는 계좌를 연결할 수 없습니다."));

        long canceled = createSaving("취소한 목표", 1_000_000);
        mockMvc.perform(patch("/api/v1/goals/" + canceled).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "CANCELED"))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/goals/" + canceled + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountNos", List.of(MAIN_NO)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOAL_NOT_EDITABLE"));

        long mine = createSaving("내 목표", 1_000_000);
        mockMvc.perform(put("/api/v1/goals/" + mine + "/accounts").header(HttpHeaders.AUTHORIZATION, otherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountNos", List.of(OTHER_USER_NO)))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/goals/" + mine + "/accounts").header(HttpHeaders.AUTHORIZATION, otherBearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/goals/1/accounts")).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private long createSaving(String name, long targetAmount) throws Exception {
        return create(Map.of("name", name, "type", "SAVING", "targetAmount", targetAmount,
                "targetDate", today.plusMonths(6).toString()));
    }

    private long createSpending(String name, long monthlyBudget) throws Exception {
        return create(Map.of("name", name, "type", "SPENDING", "monthlyBudget", monthlyBudget));
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

    private void link(long goalId, List<String> accountNos) throws Exception {
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountNos", accountNos))))
                .andExpect(status().isOk());
    }

    private void insertAccount(Long accountId, Long ownerId, String accountNo, String name, String type, String status, long balance) {
        jdbcTemplate.update(
                """
                INSERT INTO account (
                    account_id, user_id, bank_code, bank_name, account_no_encrypted, account_no_hash,
                    account_no_masked, account_name, account_type, status, balance, last_transaction_date,
                    currency, source_type, created_at, updated_at
                ) VALUES (?, ?, '004', 'KB국민은행', X'00', ?, '123456-**-**3456', ?, ?, ?, ?, NULL, 'KRW', 'SEED', ?, ?)
                """,
                accountId, ownerId, sensitiveNumberHasher.hash(accountNo), name, type, status, balance, SEEDED_AT, SEEDED_AT
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
