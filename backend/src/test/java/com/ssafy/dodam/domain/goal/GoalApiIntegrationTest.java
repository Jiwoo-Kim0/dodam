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
 * S15P21D105-508 목표 목록·요약·생성·상세·수정·삭제. 실제 회원가입·로그인 토큰으로 호출하고 목표는 API로 만든다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = "/sql/goal-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class GoalApiIntegrationTest {

    private static final String EMAIL = "goal@example.com";
    private static final String OTHER_EMAIL = "other-goal@example.com";
    private static final String PASSWORD = "password123";

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

    private String bearer;
    private String otherBearer;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        jdbcTemplate.update("DELETE FROM spending_goal");
        jdbcTemplate.update("DELETE FROM saving_goal");
        jdbcTemplate.update("DELETE FROM goal");
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
        bearer = login(EMAIL);
        otherBearer = login(OTHER_EMAIL);
    }

    // ---------- 생성 ----------

    @Test
    void createsSavingGoal() throws Exception {
        LocalDate targetDate = today.plusMonths(6);
        mockMvc.perform(post("/api/v1/goals")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "  유럽 여행  ", "type", "saving",
                                "targetAmount", 3_000_000, "targetDate", targetDate.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.goalId").isNumber())
                .andExpect(jsonPath("$.data.name").value("유럽 여행"))
                .andExpect(jsonPath("$.data.type").value("SAVING"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.currentAmount").value(0))
                .andExpect(jsonPath("$.data.targetAmount").value(3_000_000))
                .andExpect(jsonPath("$.data.targetDate").value(targetDate.toString()))
                .andExpect(jsonPath("$.data.remainingAmount").value(3_000_000))
                .andExpect(jsonPath("$.data.progressRatePercent").value(0.0))
                .andExpect(jsonPath("$.data.daysRemaining").value(java.time.temporal.ChronoUnit.DAYS.between(today, targetDate)))
                .andExpect(jsonPath("$.data.monthlyBudget").value(nullValue()))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM saving_goal", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT goal_type FROM saving_goal", String.class)).isEqualTo("SAVING");
    }

    @Test
    void createsSpendingGoal() throws Exception {
        mockMvc.perform(post("/api/v1/goals")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "월 50만 원", "type", "SPENDING", "monthlyBudget", 500_000))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("SPENDING"))
                .andExpect(jsonPath("$.data.monthlyBudget").value(500_000))
                .andExpect(jsonPath("$.data.targetAmount").value(nullValue()))
                .andExpect(jsonPath("$.data.targetDate").value(nullValue()))
                .andExpect(jsonPath("$.data.progressRatePercent").value(nullValue()));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM spending_goal", Long.class)).isEqualTo(1L);
    }

    @Test
    void allowsOnlyOneOpenSpendingGoalPerUser() throws Exception {
        long first = createSpending("월 50만 원", 500_000);

        // 진행 중(ACTIVE) 줄이기 목표가 있으면 두 번째는 409
        mockMvc.perform(post("/api/v1/goals")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "월 30만 원", "type", "SPENDING", "monthlyBudget", 300_000))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SPENDING_GOAL_ALREADY_EXISTS"));

        // PAUSED도 진행 중으로 본다
        patchGoal(first, Map.of("status", "PAUSED"));
        mockMvc.perform(post("/api/v1/goals")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "월 30만 원", "type", "SPENDING", "monthlyBudget", 300_000))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SPENDING_GOAL_ALREADY_EXISTS"));

        // 모으기 목표와 다른 사용자의 줄이기 목표는 제한과 무관하다
        createSaving("여행", 1_000_000, today.plusMonths(6));
        createSpendingAs(otherBearer, "타인 월 20만 원", 200_000);

        // 종료(CANCELED)하면 새로 만들 수 있다
        patchGoal(first, Map.of("status", "CANCELED"));
        long second = createSpending("월 30만 원", 300_000);

        // 삭제(soft delete)된 목표도 세지 않는다
        mockMvc.perform(delete("/api/v1/goals/" + second).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
        createSpending("월 20만 원", 200_000);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM goal WHERE type = 'SPENDING' AND deleted_at IS NULL AND status IN ('ACTIVE','PAUSED')",
                Long.class)).isEqualTo(2L); // 본인 1 + 타인 1
    }

    @Test
    void rejectsInvalidCreateRequests() throws Exception {
        // 필수값 누락
        mockMvc.perform(post("/api/v1/goals").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors", hasSize(2)));
        // 알 수 없는 유형
        mockMvc.perform(post("/api/v1/goals").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "x", "type", "INVEST", "targetAmount", 1, "targetDate", today.plusDays(1).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지원하지 않는 목표 유형입니다."));
        // 모으기: 목표 금액·목표일 누락
        mockMvc.perform(post("/api/v1/goals").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "x", "type", "SAVING", "targetAmount", 1000))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("모으기 목표는 목표 금액과 목표일이 필수입니다."));
        // 모으기: 목표일이 오늘
        mockMvc.perform(post("/api/v1/goals").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "x", "type", "SAVING", "targetAmount", 1000, "targetDate", today.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("목표일은 오늘 이후여야 합니다."));
        // 모으기에 월 예산
        mockMvc.perform(post("/api/v1/goals").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "x", "type", "SAVING", "targetAmount", 1000,
                                "targetDate", today.plusDays(1).toString(), "monthlyBudget", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("모으기 목표에는 월 예산을 지정할 수 없습니다."));
        // 줄이기: 월 예산 누락 / 목표 금액 지정
        mockMvc.perform(post("/api/v1/goals").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "x", "type", "SPENDING"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("줄이기 목표는 월 예산이 필수입니다."));
        mockMvc.perform(post("/api/v1/goals").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "x", "type", "SPENDING", "monthlyBudget", 1000, "targetAmount", 5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("줄이기 목표에는 목표 금액·목표일을 지정할 수 없습니다."));
        // 음수 금액
        mockMvc.perform(post("/api/v1/goals").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "x", "type", "SPENDING", "monthlyBudget", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM goal", Long.class)).isZero();
    }

    // ---------- 목록·요약 ----------

    @Test
    void listsGoalsOrderedByStatusThenNewestWithFilters() throws Exception {
        long saving1 = createSaving("첫 목표", 1_000_000, today.plusMonths(3));
        // 줄이기 목표는 진행 중인 것이 하나만 허용되므로, 지울 목표를 먼저 만들어 삭제한 뒤 실제 줄이기 목표를 만든다
        long deleted = createSpending("지운 목표", 1);
        mockMvc.perform(delete("/api/v1/goals/" + deleted).header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());
        long spending = createSpending("줄이기", 300_000);
        long saving2 = createSaving("둘째 목표", 2_000_000, today.plusMonths(12));
        long paused = createSaving("쉬는 목표", 500_000, today.plusMonths(1));
        patchGoal(paused, Map.of("status", "PAUSED"));
        createSpendingAs(otherBearer, "타인 목표", 9_999);

        mockMvc.perform(get("/api/v1/goals").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.content", hasSize(4)))
                .andExpect(jsonPath("$.data.content[0].goalId").value(saving2))
                .andExpect(jsonPath("$.data.content[1].goalId").value(spending))
                .andExpect(jsonPath("$.data.content[2].goalId").value(saving1))
                .andExpect(jsonPath("$.data.content[3].goalId").value(paused))
                .andExpect(jsonPath("$.data.content[3].status").value("PAUSED"));

        mockMvc.perform(get("/api/v1/goals").param("type", "saving").param("status", "ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].goalId").value(saving2))
                .andExpect(jsonPath("$.data.content[1].goalId").value(saving1));

        mockMvc.perform(get("/api/v1/goals").param("page", "1").param("size", "3")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].goalId").value(paused));

        mockMvc.perform(get("/api/v1/goals").param("status", "DONE").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지원하지 않는 목표 상태입니다."));
    }

    @Test
    void summarizesGoalsByStatusAndType() throws Exception {
        long saving1 = createSaving("첫 목표", 1_000_000, today.plusMonths(3));
        createSaving("둘째 목표", 3_000_000, today.plusMonths(12));
        // 줄이기 목표는 진행 중인 것이 하나만 허용된다. 종료(CANCELED)한 뒤에는 새로 만들 수 있고 요약 개수에는 둘 다 잡힌다.
        long spendingCanceled = createSpending("끝낸 줄이기", 200_000);
        patchGoal(spendingCanceled, Map.of("status", "CANCELED"));
        long spendingActive = createSpending("줄이기", 300_000);
        long achieved = createSaving("끝난 목표", 100, today.plusMonths(1));
        patchGoal(achieved, Map.of("status", "ACHIEVED"));
        jdbcTemplate.update("UPDATE goal SET current_amount = 250000 WHERE goal_id = ?", saving1);

        mockMvc.perform(get("/api/v1/goals/summary").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(5))
                .andExpect(jsonPath("$.data.activeCount").value(3))
                .andExpect(jsonPath("$.data.pausedCount").value(0))
                .andExpect(jsonPath("$.data.achievedCount").value(1))
                .andExpect(jsonPath("$.data.canceledCount").value(1))
                .andExpect(jsonPath("$.data.savingGoalCount").value(3))
                .andExpect(jsonPath("$.data.activeSavingTargetAmount").value(4_000_000))
                .andExpect(jsonPath("$.data.activeSavingCurrentAmount").value(250_000))
                .andExpect(jsonPath("$.data.activeSavingProgressPercent").value(6.3))
                .andExpect(jsonPath("$.data.spendingGoalCount").value(2))
                .andExpect(jsonPath("$.data.activeMonthlyBudget").value(300_000));
        assertThat(spendingActive).isPositive();
    }

    // ---------- 상세 ----------

    @Test
    void getsOwnedGoalAndHidesOthers() throws Exception {
        long goalId = createSaving("상세", 1_000_000, today.plusMonths(2));
        jdbcTemplate.update("UPDATE goal SET current_amount = 400000 WHERE goal_id = ?", goalId);

        mockMvc.perform(get("/api/v1/goals/" + goalId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("상세"))
                .andExpect(jsonPath("$.data.currentAmount").value(400_000))
                .andExpect(jsonPath("$.data.remainingAmount").value(600_000))
                .andExpect(jsonPath("$.data.progressRatePercent").value(40.0));

        mockMvc.perform(get("/api/v1/goals/" + goalId).header(HttpHeaders.AUTHORIZATION, otherBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("목표를 찾을 수 없습니다."));
        mockMvc.perform(get("/api/v1/goals/999999").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/goals/0").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---------- 수정 ----------

    @Test
    void updatesSavingGoalFieldsAndStatus() throws Exception {
        long goalId = createSaving("원래 이름", 1_000_000, today.plusMonths(2));
        LocalDate newDate = today.plusMonths(4);

        mockMvc.perform(patch("/api/v1/goals/" + goalId)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", " 새 이름 ", "targetAmount", 1_500_000, "targetDate", newDate.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새 이름"))
                .andExpect(jsonPath("$.data.targetAmount").value(1_500_000))
                .andExpect(jsonPath("$.data.targetDate").value(newDate.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(patch("/api/v1/goals/" + goalId)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "paused"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"));

        mockMvc.perform(patch("/api/v1/goals/" + goalId)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(jdbcTemplate.queryForObject("SELECT target_amount FROM saving_goal WHERE goal_id = ?", Long.class, goalId))
                .isEqualTo(1_500_000L);
    }

    @Test
    void duplicateUpdateKeepsUpdatedAt() throws Exception {
        long goalId = createSpending("예산", 300_000);
        String before = jdbcTemplate.queryForObject("SELECT updated_at FROM goal WHERE goal_id = ?", String.class, goalId);

        mockMvc.perform(patch("/api/v1/goals/" + goalId)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "예산", "monthlyBudget", 300_000, "status", "ACTIVE"))))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT updated_at FROM goal WHERE goal_id = ?", String.class, goalId))
                .isEqualTo(before);
    }

    @Test
    void rejectsInvalidUpdates() throws Exception {
        long saving = createSaving("모으기", 1_000_000, today.plusMonths(2));
        long spending = createSpending("줄이기", 300_000);

        mockMvc.perform(patch("/api/v1/goals/" + saving).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_FIELDS_TO_UPDATE"));
        mockMvc.perform(patch("/api/v1/goals/" + saving).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("monthlyBudget", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("모으기 목표에는 월 예산을 지정할 수 없습니다."));
        mockMvc.perform(patch("/api/v1/goals/" + saving).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("targetDate", today.minusDays(1).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("목표일은 오늘 이후여야 합니다."));
        mockMvc.perform(patch("/api/v1/goals/" + spending).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("targetAmount", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("줄이기 목표에는 목표 금액·목표일을 지정할 수 없습니다."));
        mockMvc.perform(patch("/api/v1/goals/" + spending).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("목표 이름은 비어 있을 수 없습니다."));
        mockMvc.perform(patch("/api/v1/goals/" + spending).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "DONE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지원하지 않는 목표 상태입니다."));
        // 이름 검증 실패와 함께 온 상태 변경도 적용되지 않는다
        mockMvc.perform(patch("/api/v1/goals/" + spending).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "PAUSED", "targetAmount", 1))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/goals/" + spending).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void enforcesStatusTransitions() throws Exception {
        long goalId = createSpending("전이", 300_000);

        // PAUSED → ACHIEVED 불가
        patchGoal(goalId, Map.of("status", "PAUSED"));
        mockMvc.perform(patch("/api/v1/goals/" + goalId).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "ACHIEVED"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOAL_STATUS_TRANSITION_NOT_ALLOWED"));

        // PAUSED → CANCELED 가능, 이후에는 어떤 수정도 불가
        patchGoal(goalId, Map.of("status", "CANCELED"));
        mockMvc.perform(patch("/api/v1/goals/" + goalId).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "되살리기"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOAL_NOT_EDITABLE"));
        mockMvc.perform(patch("/api/v1/goals/" + goalId).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "ACTIVE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOAL_NOT_EDITABLE"));
    }

    @Test
    void cannotUpdateOtherUsersGoal() throws Exception {
        long goalId = createSpending("내 목표", 300_000);
        mockMvc.perform(patch("/api/v1/goals/" + goalId).header(HttpHeaders.AUTHORIZATION, otherBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "침입"))))
                .andExpect(status().isNotFound());
    }

    // ---------- 삭제 ----------

    @Test
    void softDeletesGoal() throws Exception {
        long goalId = createSaving("지울 목표", 1_000_000, today.plusMonths(2));

        mockMvc.perform(delete("/api/v1/goals/" + goalId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/v1/goals/" + goalId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/goals/" + goalId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
        // 행은 남고 deleted_at만 찍힌다. 하위행도 남는다
        assertThat(jdbcTemplate.queryForObject("SELECT deleted_at FROM goal WHERE goal_id = ?", Object.class, goalId)).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM saving_goal WHERE goal_id = ?", Long.class, goalId)).isEqualTo(1L);
    }

    @Test
    void cannotDeleteOtherUsersGoal() throws Exception {
        long goalId = createSpending("내 목표", 300_000);
        mockMvc.perform(delete("/api/v1/goals/" + goalId).header(HttpHeaders.AUTHORIZATION, otherBearer))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("SELECT deleted_at FROM goal WHERE goal_id = ?", Object.class, goalId)).isNull();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/goals")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private long createSaving(String name, long targetAmount, LocalDate targetDate) throws Exception {
        return createAs(bearer, Map.of("name", name, "type", "SAVING", "targetAmount", targetAmount, "targetDate", targetDate.toString()));
    }

    private long createSpending(String name, long monthlyBudget) throws Exception {
        return createSpendingAs(bearer, name, monthlyBudget);
    }

    private long createSpendingAs(String token, String name, long monthlyBudget) throws Exception {
        return createAs(token, Map.of("name", name, "type", "SPENDING", "monthlyBudget", monthlyBudget));
    }

    private long createAs(String token, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/goals")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new HashMap<>(body))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("goalId").asLong();
    }

    private void patchGoal(long goalId, Map<String, Object> body) throws Exception {
        mockMvc.perform(patch("/api/v1/goals/" + goalId)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk());
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
