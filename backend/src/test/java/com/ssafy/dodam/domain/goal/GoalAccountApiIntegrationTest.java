package com.ssafy.dodam.domain.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.web.servlet.ResultActions;

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
    private static final long MAIN_ID = 11L;
    private static final long SAVINGS_ID = 12L;
    private static final long DEPOSIT_ID = 13L;
    private static final long MINUS_ID = 14L;
    private static final long CLOSED_ID = 15L;
    private static final long OTHER_USER_ID = 21L;
    private static final long UNKNOWN_ID = 999L;
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
        allocate(goalId, MAIN_ID, 1_850_000).andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountIds", List.of(SAVINGS_ID, SAVINGS_ID)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goalId").value(goalId))
                .andExpect(jsonPath("$.data.linkedCount").value(2))
                .andExpect(jsonPath("$.data.totalBalance").value(2_450_000))
                .andExpect(jsonPath("$.data.accounts", hasSize(2)))
                .andExpect(jsonPath("$.data.accounts[0].accountId").value(11))
                .andExpect(jsonPath("$.data.accounts[0].accountNoMasked").value("123456-**-**3456"))
                .andExpect(jsonPath("$.data.accounts[0].accountType").value("DEMAND_DEPOSIT"))
                .andExpect(jsonPath("$.data.accounts[0].balance").value(1_850_000))
                .andExpect(jsonPath("$.data.accounts[0].contributionAmount").value(1_850_000))
                .andExpect(jsonPath("$.data.accounts[0].allocatedToThisGoal").value(1_850_000))
                .andExpect(jsonPath("$.data.accounts[0].allocatedElsewhere").value(0))
                .andExpect(jsonPath("$.data.accounts[0].availableToAllocate").value(0))
                .andExpect(jsonPath("$.data.accounts[0].shortfallAmount").value(0))
                .andExpect(jsonPath("$.data.accounts[0].allocationStatus").value("OK"))
                .andExpect(jsonPath("$.data.accounts[1].accountId").value(12))
                .andExpect(jsonPath("$.data.accounts[1].status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.accounts[1].allocatedToThisGoal").value(600_000))
                .andExpect(jsonPath("$.data.accounts[1].availableToAllocate").value(0))
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

    /**
     * 626 확보액 정의: 입출금 링크의 `contribution_amount`가 있으면 그 금액만, 없으면 잔액 전체. 같은 입출금 계좌가 다른 목표에도
     * 담겨 있으면 그만큼 `allocatedElsewhere`·가용액이 줄고, 실제 잔액이 총 할당 아래로 내려가면 계좌 단위 부족을 표시한다.
     * 담기 API로 두 목표에 금액을 나눠 넣어 읽기 경로까지 검증한다.
     */
    @Test
    void demandDepositContributionIsReadAsAllocatedAmountAndSharedAcrossGoals() throws Exception {
        long goalA = createSaving("A 목표", 3_000_000);
        long goalB = createSaving("B 목표", 1_000_000);
        allocate(goalA, MAIN_ID, 500_000).andExpect(status().isOk());
        link(goalA, List.of(SAVINGS_ID));
        allocate(goalB, MAIN_ID, 300_000).andExpect(status().isOk());

        // A 확보액 = 입출금 담은 500,000 + 적금 전액 600,000. 잔액 1,850,000 중 다른 목표(B)에 300,000이 담겨 있다
        mockMvc.perform(get("/api/v1/goals/" + goalA + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBalance").value(1_100_000))
                .andExpect(jsonPath("$.data.accounts[0].accountId").value(11))
                .andExpect(jsonPath("$.data.accounts[0].balance").value(1_850_000))
                .andExpect(jsonPath("$.data.accounts[0].contributionAmount").value(500_000))
                .andExpect(jsonPath("$.data.accounts[0].allocatedToThisGoal").value(500_000))
                .andExpect(jsonPath("$.data.accounts[0].allocatedElsewhere").value(300_000))
                .andExpect(jsonPath("$.data.accounts[0].availableToAllocate").value(1_050_000))
                .andExpect(jsonPath("$.data.accounts[0].shortfallAmount").value(0))
                .andExpect(jsonPath("$.data.accounts[0].allocationStatus").value("OK"))
                .andExpect(jsonPath("$.data.accounts[1].allocatedToThisGoal").value(600_000));

        // 목표 계산(510)도 같은 정의를 쓴다: 연결 계좌 잔액 합 2,450,000이 아니라 확보액 1,100,000. 조회 시점에 저장값도 갱신된다
        mockMvc.perform(get("/api/v1/goals/" + goalA + "/progress").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentAmount").value(1_100_000));
        mockMvc.perform(get("/api/v1/goals/" + goalA).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentAmount").value(1_100_000));
        mockMvc.perform(get("/api/v1/goals/" + goalB + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBalance").value(300_000))
                .andExpect(jsonPath("$.data.accounts[0].allocatedElsewhere").value(500_000));

        // 실제 출금으로 잔액 700,000: 총 할당 800,000이 잔액을 100,000 넘는다. 설정액은 유지하고 부족만 표시, 가용액 0
        jdbcTemplate.update("UPDATE account SET balance = 700000 WHERE account_id = ?", MAIN_ID);
        mockMvc.perform(get("/api/v1/goals/" + goalA + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBalance").value(1_100_000))
                .andExpect(jsonPath("$.data.accounts[0].allocatedToThisGoal").value(500_000))
                .andExpect(jsonPath("$.data.accounts[0].availableToAllocate").value(0))
                .andExpect(jsonPath("$.data.accounts[0].shortfallAmount").value(100_000))
                .andExpect(jsonPath("$.data.accounts[0].allocationStatus").value("NEEDS_ADJUSTMENT"));

        // B를 삭제하면 그 할당은 가용액 계산에서 빠진다(삭제 목표의 링크는 합산하지 않는다)
        mockMvc.perform(delete("/api/v1/goals/" + goalB).header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/goals/" + goalA + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accounts[0].allocatedElsewhere").value(0))
                .andExpect(jsonPath("$.data.accounts[0].availableToAllocate").value(200_000))
                .andExpect(jsonPath("$.data.accounts[0].shortfallAmount").value(0))
                .andExpect(jsonPath("$.data.accounts[0].allocationStatus").value("OK"));
    }

    @Test
    void listsLinkedAccountsWithPaging() throws Exception {
        long goalId = createSaving("목표", 20_000_000);
        allocate(goalId, MAIN_ID, 1_850_000).andExpect(status().isOk());
        link(goalId, List.of(SAVINGS_ID, DEPOSIT_ID));

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
        allocate(goalId, MAIN_ID, 1_850_000).andExpect(status().isOk());
        link(goalId, List.of(SAVINGS_ID));

        // 예·적금 하나를 교체해도 입출금 할당은 유지한다
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountIds", List.of(DEPOSIT_ID)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linkedCount").value(2))
                .andExpect(jsonPath("$.data.totalBalance").value(11_850_000))
                .andExpect(jsonPath("$.data.accounts[0].accountId").value(11))
                .andExpect(jsonPath("$.data.accounts[1].accountId").value(13));
        assertThat(jdbcTemplate.queryForList("SELECT account_id FROM goal_account WHERE goal_id = ? ORDER BY account_id",
                Long.class, goalId)).containsExactly(11L, 13L);

        // 빈 배열 → 예·적금만 해제하고 입출금 할당은 보존
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountIds", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linkedCount").value(1))
                .andExpect(jsonPath("$.data.totalBalance").value(1_850_000))
                .andExpect(jsonPath("$.data.accounts[0].accountId").value(MAIN_ID));
        assertThat(jdbcTemplate.queryForObject("SELECT current_amount FROM goal WHERE goal_id = ?", Long.class, goalId))
                .isEqualTo(1_850_000L);
    }

    @Test
    void sameSetIsIdempotentAndRefreshesBalance() throws Exception {
        long goalId = createSaving("목표", 5_000_000);
        link(goalId, List.of(SAVINGS_ID));
        String updatedAt = jdbcTemplate.queryForObject("SELECT updated_at FROM goal WHERE goal_id = ?", String.class, goalId);

        // 같은 집합이면 연결 행은 그대로, 잔액이 안 바뀌면 updated_at도 그대로
        link(goalId, List.of(SAVINGS_ID));
        assertThat(jdbcTemplate.queryForObject("SELECT updated_at FROM goal WHERE goal_id = ?", String.class, goalId))
                .isEqualTo(updatedAt);

        // 잔액이 바뀐 뒤 같은 집합으로 다시 PUT하면 모인 금액만 갱신된다
        jdbcTemplate.update("UPDATE account SET balance = 2000000 WHERE account_id = ?", SAVINGS_ID);
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountIds", List.of(SAVINGS_ID)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBalance").value(2_000_000));
        assertThat(jdbcTemplate.queryForObject("SELECT current_amount FROM goal WHERE goal_id = ?", Long.class, goalId))
                .isEqualTo(2_000_000L);
    }

    @Test
    void rejectsUnlinkableAccountsWithoutPartialChange() throws Exception {
        long goalId = createSaving("목표", 5_000_000);
        allocate(goalId, MAIN_ID, 300_000).andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountIds", List.of(MAIN_ID)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("예금·적금 계좌만 연결할 수 있습니다. 입출금 계좌는 담기·꺼내기를 이용해 주세요."));

        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountIds", List.of(SAVINGS_ID, MINUS_ID)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("예금·적금 계좌만 연결할 수 있습니다. 입출금 계좌는 담기·꺼내기를 이용해 주세요."));
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountIds", List.of(CLOSED_ID)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("예금·적금 계좌만 연결할 수 있습니다. 입출금 계좌는 담기·꺼내기를 이용해 주세요."));
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountIds", List.of(OTHER_USER_ID)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("계좌를 찾을 수 없습니다."));
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountIds", List.of(UNKNOWN_ID)))))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountIds", List.of(0L)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountIds\": [null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        // 계좌번호 원문(이전 계약)을 보내면 숫자로 읽을 수 없어 400
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountNos", List.of(MAIN_NO)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 기존 입출금 할당은 그대로
        assertThat(jdbcTemplate.queryForList("SELECT account_id FROM goal_account WHERE goal_id = ?", Long.class, goalId))
                .containsExactly(11L);
    }

    @Test
    void allocationPutsAbsoluteAmountAndIsIdempotent() throws Exception {
        // 703 담기·꺼내기: 절대값 하나로 담기·꺼내기·해제를 모두 처리한다. MAIN 잔액 1,850,000
        long goalId = createSaving("세이프박스", 5_000_000);

        // 담기 — 연결이 없던 계좌도 담으면 생긴다
        allocate(goalId, MAIN_ID, 500_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBalance").value(500_000))
                .andExpect(jsonPath("$.data.accounts[0].allocatedToThisGoal").value(500_000))
                .andExpect(jsonPath("$.data.accounts[0].availableToAllocate").value(1_350_000))
                .andExpect(jsonPath("$.data.accounts[0].allocationStatus").value("OK"));

        // 같은 요청을 다시 보내도 결과가 같다(멱등)
        allocate(goalId, MAIN_ID, 500_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBalance").value(500_000));

        // 꺼내기 — 더 작은 값
        allocate(goalId, MAIN_ID, 200_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBalance").value(200_000))
                .andExpect(jsonPath("$.data.accounts[0].availableToAllocate").value(1_650_000));

        // 0은 할당 해제
        allocate(goalId, MAIN_ID, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linkedCount").value(0))
                .andExpect(jsonPath("$.data.totalBalance").value(0));
        assertThat(jdbcTemplate.queryForList("SELECT account_id FROM goal_account WHERE goal_id = ?", Long.class, goalId))
                .isEmpty();
    }

    @Test
    void allocationSplitsOneAccountAcrossGoalsAndCapsAtAvailable() throws Exception {
        // 703: 같은 통장을 두 목표가 나눠 담는다. 잔액 1,850,000
        long goalA = createSaving("A 목표", 5_000_000);
        long goalB = createSaving("B 목표", 5_000_000);
        allocate(goalA, MAIN_ID, 1_000_000).andExpect(status().isOk());

        allocate(goalB, MAIN_ID, 800_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accounts[0].allocatedToThisGoal").value(800_000))
                .andExpect(jsonPath("$.data.accounts[0].allocatedElsewhere").value(1_000_000))
                .andExpect(jsonPath("$.data.accounts[0].availableToAllocate").value(50_000));

        // 남은 50,000을 넘으면 거절한다. 다른 목표 할당은 그대로다
        allocate(goalB, MAIN_ID, 900_000)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOAL_ALLOCATION_EXCEEDS_AVAILABLE"));
        mockMvc.perform(get("/api/v1/goals/" + goalA + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.data.totalBalance").value(1_000_000));
    }

    @Test
    void concurrentAllocationsCannotExceedAccountBalance() throws Exception {
        long goalA = createSaving("동시 담기 A", 5_000_000);
        long goalB = createSaving("동시 담기 B", 5_000_000);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Integer> first = executor.submit(() -> concurrentAllocate(goalA, ready, start));
            Future<Integer> second = executor.submit(() -> concurrentAllocate(goalB, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(contribution_amount), 0) FROM goal_account WHERE account_id = ?",
                    Long.class, MAIN_ID)).isEqualTo(1_000_000L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void allocationRejectsSavingsAccountAndAllowsWithdrawFromTerminalGoal() throws Exception {
        long goalId = createSaving("세이프박스 검증", 5_000_000);

        // 예·적금은 계좌 하나가 목표 하나에 전액 연결되므로 나눠 담을 금액이라는 개념이 없다
        allocate(goalId, SAVINGS_ID, 100_000)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("자유입출금 계좌만 목표에 담을 수 있습니다."));

        allocate(goalId, MAIN_ID, 400_000).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/goals/" + goalId).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "CANCELED"))))
                .andExpect(status().isOk());

        // 종료된 목표는 늘리기만 막는다 — 담아 둔 돈을 되찾을 길은 열어 둔다
        allocate(goalId, MAIN_ID, 500_000)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOAL_NOT_EDITABLE"));
        allocate(goalId, MAIN_ID, 100_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBalance").value(100_000));
        allocate(goalId, MAIN_ID, 0).andExpect(status().isOk());
    }

    @Test
    void allocationValidatesInputOwnershipStatusAndAuthentication() throws Exception {
        long goalId = createSaving("담기 검증", 5_000_000);

        mockMvc.perform(put("/api/v1/goals/" + goalId + "/allocations/" + MAIN_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/allocations/" + MAIN_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        allocate(goalId, CLOSED_ID, 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("해지된 계좌는 목표에 연결할 수 없습니다."));
        allocate(goalId, OTHER_USER_ID, 1).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/allocations/" + OTHER_USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, otherBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/allocations/" + MAIN_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/goals/" + goalId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
        allocate(goalId, MAIN_ID, 1).andExpect(status().isNotFound());
    }

    @Test
    void rejectsAccountLinkedToAnotherLiveGoalButTakesOverFromDeletedGoal() throws Exception {
        long first = createSaving("첫 목표", 5_000_000);
        long second = createSaving("둘째 목표", 5_000_000);
        link(first, List.of(SAVINGS_ID));

        mockMvc.perform(put("/api/v1/goals/" + second + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountIds", List.of(SAVINGS_ID)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_LINKED_TO_GOAL"));

        // 첫 목표를 지우면(soft delete) 둘째 목표가 그 계좌를 이어받는다
        mockMvc.perform(delete("/api/v1/goals/" + first).header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/goals/" + second + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountIds", List.of(SAVINGS_ID)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.linkedCount").value(1));
        assertThat(jdbcTemplate.queryForList("SELECT goal_id FROM goal_account WHERE account_id = ?", Long.class, SAVINGS_ID))
                .containsExactly(second);
    }

    @Test
    void rejectsSpendingGoalTerminalGoalAndOtherUsersGoal() throws Exception {
        long spending = createSpending("줄이기", 300_000);
        mockMvc.perform(put("/api/v1/goals/" + spending + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountIds", List.of(SAVINGS_ID)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("줄이기 목표에는 계좌를 연결할 수 없습니다."));

        long canceled = createSaving("취소한 목표", 1_000_000);
        mockMvc.perform(patch("/api/v1/goals/" + canceled).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "CANCELED"))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/goals/" + canceled + "/accounts").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountIds", List.of(SAVINGS_ID)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOAL_NOT_EDITABLE"));

        long mine = createSaving("내 목표", 1_000_000);
        mockMvc.perform(put("/api/v1/goals/" + mine + "/accounts").header(HttpHeaders.AUTHORIZATION, otherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountIds", List.of(OTHER_USER_ID)))))
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

    /** 703 담기·꺼내기. 절대값이라 담기·꺼내기·해제가 모두 같은 호출이다. */
    private ResultActions allocate(long goalId, long accountId, long amount) throws Exception {
        return mockMvc.perform(put("/api/v1/goals/" + goalId + "/allocations/" + accountId)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("amount", amount))));
    }

    private int concurrentAllocate(long goalId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 담기 시작 신호를 기다리지 못했습니다.");
        }
        return allocate(goalId, MAIN_ID, 1_000_000).andReturn().getResponse().getStatus();
    }

    private void link(long goalId, List<Long> accountIds) throws Exception {
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("accountIds", accountIds))))
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
