package com.ssafy.dodam.domain.improvement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.diagnosis.service.DiagnosisClock;
import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.entity.SavingGoal;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.goal.repository.SavingGoalRepository;
import com.ssafy.dodam.domain.goal.service.GoalClock;
import com.ssafy.dodam.domain.improvement.repository.ImprovementActionRepository;
import com.ssafy.dodam.domain.improvement.service.ImprovementClock;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationRepository;
import com.ssafy.dodam.domain.recommendation.service.RecommendationClock;
import com.ssafy.dodam.domain.simulation.service.SimulationClock;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API 19 Improvement(S15P21D105-590).
 *
 * <p>개선 행동은 추천 수락으로만 만들어지므로 입구를 실제 상태 변경 API로 통과시킨다. 시각은 전부 고정 시계를
 * 갈아 끼워 "수락한 달", "완료된 달", "측정 시점"을 눈에 보이게 옮긴다 — 측정 기간 정책이 깨지면 여기서 드러난다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:improvement590;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class ImprovementApiTest {

    /** 수락 시점. before = 2026-06-01~08-31, after 시작 = 2026-10-01이다. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 16, 12, 0);
    /** 2026-10이 완료된 시점. after 창이 1개월로 열린다. */
    private static final LocalDateTime AFTER_ONE_MONTH = LocalDateTime.of(2026, 11, 5, 9, 0);
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired GoalRepository goals;
    @Autowired SavingGoalRepository savingGoals;
    @Autowired RecommendationRepository recommendations;
    @Autowired ImprovementActionRepository actions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean RecommendationClock recommendationClock;
    @MockitoBean ImprovementClock improvementClock;
    @MockitoBean DiagnosisClock diagnosisClock;
    @MockitoBean SimulationClock simulationClock;
    @MockitoBean GoalClock goalClock;

    private Long owner;
    private Long other;
    private Long goalId;

    @BeforeEach
    void setup() {
        given(recommendationClock.now()).willReturn(NOW);
        given(improvementClock.now()).willReturn(NOW);
        given(improvementClock.today()).willReturn(NOW.toLocalDate());
        given(diagnosisClock.now()).willReturn(NOW);
        given(simulationClock.now()).willReturn(NOW);
        given(goalClock.now()).willReturn(NOW);
        given(goalClock.today()).willReturn(NOW.toLocalDate());
        owner = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "개선")).getId();
        other = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "다른 사용자")).getId();
        goalId = goals.save(Goal.create(owner, "비상금", GoalType.SAVING, NOW.minusMonths(6))).getId();
        savingGoals.save(SavingGoal.create(goalId, 10_000_000L, NOW.toLocalDate().plusYears(2)));
    }

    @Test
    void acceptCreatesOneActionAndKeepsExpectedApartFromActual() throws Exception {
        long recommendationId = recommend();
        accept(recommendationId);

        mvc.perform(get("/api/v1/improvements/actions").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].recommendationId").value(recommendationId))
                .andExpect(jsonPath("$.data.content[0].goalId").value(goalId))
                .andExpect(jsonPath("$.data.content[0].domain").value("SPENDING"))
                .andExpect(jsonPath("$.data.content[0].status").value("PLANNED"))
                // 예상 효과는 추천에서, 실측은 행동에서. 측정 전이므로 실측은 0원이 아니라 null이다.
                .andExpect(jsonPath("$.data.content[0].expectedMonthlySaving").value(12000))
                .andExpect(jsonPath("$.data.content[0].actualMonthlySaving").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].measuredAt").doesNotExist());

        mvc.perform(get("/api/v1/improvements/summary").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalActions").value(1))
                .andExpect(jsonPath("$.data.measuredActions").value(0))
                // 4개 상태 키가 항상 모두 있다
                .andExpect(jsonPath("$.data.byStatus.PLANNED").value(1))
                .andExpect(jsonPath("$.data.byStatus.IN_PROGRESS").value(0))
                .andExpect(jsonPath("$.data.byStatus.COMPLETED").value(0))
                .andExpect(jsonPath("$.data.byStatus.CANCELED").value(0));
    }

    @Test
    void deferAndReacceptDoesNotCreateASecondAction() throws Exception {
        long recommendationId = recommend();
        accept(recommendationId);
        mvc.perform(post("/api/v1/recommendations/" + recommendationId + "/defer").with(user(owner.toString())))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/recommendations/" + recommendationId + "/accept").with(user(owner.toString())))
                .andExpect(status().isOk());

        // 같은 개선안의 효과가 두 번 집계되면 안 된다
        assertThat(actions.findAllByUserId(owner)).hasSize(1);
        mvc.perform(get("/api/v1/improvements/actions").with(user(owner.toString())))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("PLANNED"))
                .andExpect(jsonPath("$.data.content[0].canceledAt").doesNotExist());
    }

    @Test
    void rejectAfterAcceptCancelsTheAction() throws Exception {
        long recommendationId = recommend();
        accept(recommendationId);
        mvc.perform(post("/api/v1/recommendations/" + recommendationId + "/reject").with(user(owner.toString())))
                .andExpect(status().isOk());

        long actionId = actions.findAllByUserId(owner).getFirst().getActionId();
        mvc.perform(get("/api/v1/improvements/actions/" + actionId).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.canceledAt").exists());
        // 취소된 행동은 측정 대상이 아니다
        mvc.perform(get("/api/v1/improvements/actions/" + actionId + "/comparison").with(user(owner.toString())))
                .andExpect(jsonPath("$.data.unavailableReason").value("ACTION_NOT_MEASURABLE"))
                .andExpect(jsonPath("$.data.metrics").isEmpty());
    }

    @Test
    void otherUsersActionIsNotFound() throws Exception {
        long recommendationId = recommend();
        accept(recommendationId);
        long actionId = actions.findAllByUserId(owner).getFirst().getActionId();

        mvc.perform(get("/api/v1/improvements/actions/" + actionId).with(user(other.toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(get("/api/v1/improvements/actions/" + actionId + "/comparison").with(user(other.toString())))
                .andExpect(status().isNotFound());
    }

    @Test
    void comparisonIsNotReadyUntilAWholeMonthCompletes() throws Exception {
        long recommendationId = recommend();
        accept(recommendationId);
        long actionId = actions.findAllByUserId(owner).getFirst().getActionId();

        // 수락한 달(9월)은 전·후가 섞여 있어 어느 쪽에도 넣지 않는다. 10월이 끝나야 비교가 열린다.
        mvc.perform(get("/api/v1/improvements/actions/" + actionId + "/comparison").with(user(owner.toString())))
                .andExpect(jsonPath("$.data.unavailableReason").value("AFTER_PERIOD_NOT_READY"))
                .andExpect(jsonPath("$.data.beforePeriod.startDate").value("2026-06-01"))
                .andExpect(jsonPath("$.data.beforePeriod.endDate").value("2026-08-31"));
    }

    @Test
    void measuresActualEffectOnceTheAfterMonthIsComplete() throws Exception {
        // before 3개월: 월 150만 원 소비
        expense(LocalDate.of(2026, 6, 10), 1_500_000);
        expense(LocalDate.of(2026, 7, 10), 1_500_000);
        expense(LocalDate.of(2026, 8, 10), 1_500_000);
        long recommendationId = recommend();
        accept(recommendationId);
        long actionId = actions.findAllByUserId(owner).getFirst().getActionId();

        // after 1개월: 월 100만 원 소비 → 월 50만 원 감소
        expense(LocalDate.of(2026, 10, 10), 1_000_000);
        travelTo(AFTER_ONE_MONTH);

        mvc.perform(get("/api/v1/improvements/actions/" + actionId + "/comparison").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailableReason").doesNotExist())
                .andExpect(jsonPath("$.data.afterPeriod.startDate").value("2026-10-01"))
                .andExpect(jsonPath("$.data.afterPeriod.endDate").value("2026-10-31"))
                .andExpect(jsonPath("$.data.metrics[0].metric").value("MONTHLY_SPENDING"))
                .andExpect(jsonPath("$.data.metrics[0].before").value(1_500_000))
                .andExpect(jsonPath("$.data.metrics[0].after").value(1_000_000))
                // delta는 부호를 그대로 두고, 좋아졌는지는 improved가 말한다
                .andExpect(jsonPath("$.data.metrics[0].delta").value(-500_000))
                .andExpect(jsonPath("$.data.metrics[0].improved").value(true));

        mvc.perform(get("/api/v1/improvements/savings").with(user(owner.toString())))
                .andExpect(jsonPath("$.data.metric").value("SAVINGS"))
                .andExpect(jsonPath("$.data.monthlyAmount").value(500_000))
                .andExpect(jsonPath("$.data.totalAmount").value(6_000_000))
                .andExpect(jsonPath("$.data.contributingActions").value(1))
                .andExpect(jsonPath("$.data.currency").value("KRW"));

        mvc.perform(get("/api/v1/improvements/spending-reduction").with(user(owner.toString())))
                .andExpect(jsonPath("$.data.monthlyAmount").value(500_000));
        // 부채는 잔액이라 월 개념이 없다
        mvc.perform(get("/api/v1/improvements/debt-reduction").with(user(owner.toString())))
                .andExpect(jsonPath("$.data.totalAmount").value(0))
                .andExpect(jsonPath("$.data.monthlyAmount").doesNotExist());

        // 관측이 시작됐으므로 상태가 IN_PROGRESS로 넘어간다
        mvc.perform(get("/api/v1/improvements/actions/" + actionId).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.actualMonthlySaving").value(500_000))
                .andExpect(jsonPath("$.data.expectedMonthlySaving").value(12000));
    }

    @Test
    void monthlyTrendFillsEmptyMonthsWithZero() throws Exception {
        long recommendationId = recommend();
        accept(recommendationId);

        // 월 범위 검증은 공용 YearMonthPeriodPolicy가 실제 현재 월 기준으로 한다(고정 시계를 쓰지 않는다).
        // 그래서 여기서는 지나간 월만 요청한다.
        String body = mvc.perform(get("/api/v1/improvements/monthly-trend")
                        .param("startMonth", "2026-07").param("endMonth", "2026-09")
                        .with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startMonth").value("2026-07"))
                .andExpect(jsonPath("$.data.endMonth").value("2026-09"))
                .andReturn().getResponse().getContentAsString();
        var months = json.readTree(body).at("/data/months");
        // 거래도 측정도 없는 달을 서버가 0으로 채워 연속으로 내려준다
        assertThat(months).hasSize(3);
        assertThat(months.get(0).get("yearMonth").asText()).isEqualTo("2026-07");
        assertThat(months.get(0).get("monthlySaving").asLong()).isZero();
        assertThat(months.get(1).get("yearMonth").asText()).isEqualTo("2026-08");
        assertThat(months.get(2).get("yearMonth").asText()).isEqualTo("2026-09");
    }

    @Test
    void goalImpactReportsMissingBaselineInsteadOfGuessing() throws Exception {
        long recommendationId = recommend();
        accept(recommendationId);

        // 연결 계좌가 없어 저축속도가 0이다 → 예상 달성일을 만들 수 없다. 0일 앞당겨진 것으로 꾸미지 않는다.
        mvc.perform(get("/api/v1/improvements/goal-impact").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goals[0].goalId").value(goalId))
                .andExpect(jsonPath("$.data.goals[0].contributingActions").value(1))
                .andExpect(jsonPath("$.data.goals[0].advancedDays").doesNotExist())
                .andExpect(jsonPath("$.data.goals[0].unavailableReason").value("BASELINE_FORECAST_UNAVAILABLE"));
    }

    @Test
    void rediagnoseCreatesOnceAndReusesTheSameDayResult() throws Exception {
        mvc.perform(post("/api/v1/improvements/rediagnose").with(user(owner.toString())).contentType(JSON)
                        .content("{\"goalId\":" + goalId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.created").value(true))
                .andExpect(jsonPath("$.data.goalId").value(goalId));

        // 중복 요청 방지: 같은 날 같은 목표는 직전 진단을 그대로 돌려준다
        mvc.perform(post("/api/v1/improvements/rediagnose").with(user(owner.toString())).contentType(JSON)
                        .content("{\"goalId\":" + goalId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(false));
    }

    @Test
    void listRejectsInvalidPageAndPeriod() throws Exception {
        mvc.perform(get("/api/v1/improvements/actions").param("size", "0").with(user(owner.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/improvements/actions")
                        .param("startDate", "2026-09-10").param("endDate", "2026-09-01")
                        .with(user(owner.toString())))
                .andExpect(status().isBadRequest());
    }

    // ---------- helpers ----------

    private void travelTo(LocalDateTime moment) {
        given(improvementClock.now()).willReturn(moment);
        given(improvementClock.today()).willReturn(moment.toLocalDate());
        given(goalClock.now()).willReturn(moment);
        given(goalClock.today()).willReturn(moment.toLocalDate());
    }

    /** 진단 실행을 거치지 않고 수락 대상 추천 1건만 만든다. 이 테스트의 관심사는 수락 이후다. */
    private long recommend() {
        Recommendation recommendation = Recommendation.pending(owner, 1L, goalId, null, "SPENDING",
                "CANCEL_SUBSCRIPTION", "쓰지 않는 구독을 정리하세요", "설명", 1,
                new Recommendation.Effect(12_000L, 144_000L, null, null, null, null, null, null),
                UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""), NOW);
        return recommendations.save(recommendation).getRecommendationId();
    }

    private void accept(long recommendationId) throws Exception {
        mvc.perform(post("/api/v1/recommendations/" + recommendationId + "/accept").with(user(owner.toString())))
                .andExpect(status().isOk());
    }

    private void expense(LocalDate date, long amount) {
        jdbc.update("""
                INSERT INTO `transaction` (transaction_id, user_id, account_id, card_id, category_id, transaction_date,
                    transaction_time, transaction_type, amount, balance_after, merchant_name, payment_method, spending_type,
                    summary, memo, hidden, source_type, created_at, updated_at)
                VALUES (?, ?, NULL, NULL, NULL, ?, NULL, 'EXPENSE', ?, NULL, NULL, 'CARD', 'VARIABLE', 'test', NULL, FALSE, 'SEED', ?, NULL)
                """, UUID.randomUUID().toString(), owner, date, amount, NOW);
    }
}
