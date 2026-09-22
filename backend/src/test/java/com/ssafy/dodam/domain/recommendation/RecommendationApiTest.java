package com.ssafy.dodam.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.diagnosis.service.DiagnosisClock;
import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.entity.SavingGoal;
import com.ssafy.dodam.domain.goal.entity.SpendingGoal;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.goal.repository.SavingGoalRepository;
import com.ssafy.dodam.domain.goal.repository.SpendingGoalRepository;
import com.ssafy.dodam.domain.goal.service.GoalClock;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationHistoryRepository;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationRepository;
import com.ssafy.dodam.domain.recommendation.service.RecommendationClock;
import com.ssafy.dodam.domain.simulation.service.SimulationClock;
import com.ssafy.dodam.domain.user.entity.FinancialProfile;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
 * 생성·조회(548)와 상태 변경·이력(549) API. 진단은 실제 실행(POST /diagnoses)으로 만들어 생성기가 읽는 스냅샷·문제 JSON 형식이
 * DiagnosisEvaluator와 어긋나면 여기서 드러난다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:recommendation548;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class RecommendationApiTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 13, 12, 0);
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired GoalRepository goals;
    @Autowired SavingGoalRepository savingGoals;
    @Autowired SpendingGoalRepository spendingGoals;
    @Autowired RecommendationRepository recommendations;
    @Autowired RecommendationHistoryRepository histories;
    @Autowired FinancialProfileRepository profiles;
    @Autowired JdbcTemplate jdbc;
    @Autowired SensitiveNumberHasher hasher;
    @MockitoBean DiagnosisClock diagnosisClock;
    @MockitoBean RecommendationClock clock;
    // 587: 추천 프리셋을 그대로 실행하는 검증에서 시뮬레이션 기준일을 진단 기준일과 같은 날로 고정한다
    @MockitoBean SimulationClock simulationClock;
    @MockitoBean GoalClock goalClock;
    private Long owner;
    private Long other;
    private Long goalId;

    @BeforeEach
    void setup() {
        given(diagnosisClock.now()).willReturn(NOW);
        given(clock.now()).willReturn(NOW);
        given(simulationClock.now()).willReturn(NOW);
        given(goalClock.now()).willReturn(NOW);
        given(goalClock.today()).willReturn(NOW.toLocalDate());
        owner = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "추천")).getId();
        other = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "다른 사용자")).getId();
        // 2개월 전 시작, 1개월 뒤 100만 원 목표, 연결 계좌 없음 → 속도 0, 필요 월 100만 원 → SAVING_GOAL_RISK
        Goal goal = goals.save(Goal.create(owner, "비상금", GoalType.SAVING, NOW.minusMonths(2)));
        goalId = goal.getId();
        savingGoals.save(SavingGoal.create(goalId, 1_000_000L, NOW.toLocalDate().plusMonths(1)));
    }

    private long diagnose(Long userId, Long goal) throws Exception {
        String body = mvc.perform(post("/api/v1/diagnoses").with(user(userId.toString())).contentType(JSON)
                        .content(goal == null ? "{\"forceRefresh\":true}" : "{\"goalId\":" + goal + ",\"forceRefresh\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).at("/data/diagnosis/diagnosisId").asLong();
    }

    private long generate(long diagnosisId) throws Exception {
        String body = mvc.perform(post("/api/v1/recommendations/generate").with(user(owner.toString())).contentType(JSON)
                        .content("{\"diagnosisId\":" + diagnosisId + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).at("/data/items/0/recommendation/recommendationId").asLong();
    }

    @Test
    void generateFromRealDiagnosisIsIdempotentAndExposesEffect() throws Exception {
        long diagnosisId = diagnose(owner, goalId);
        String body = mvc.perform(post("/api/v1/recommendations/generate").with(user(owner.toString())).contentType(JSON)
                        .content("{\"diagnosisId\":" + diagnosisId + ",\"goalId\":" + goalId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.reused").value(0))
                .andExpect(jsonPath("$.data.goalId").value(goalId))
                .andExpect(jsonPath("$.data.skipped").isEmpty())
                .andExpect(jsonPath("$.data.items[0].created").value(true))
                .andExpect(jsonPath("$.data.items[0].score").isNumber())
                .andExpect(jsonPath("$.data.items[0].recommendation.domain").value("SAVING"))
                .andExpect(jsonPath("$.data.items[0].recommendation.type").value("INCREASE_MONTHLY_SAVING"))
                .andExpect(jsonPath("$.data.items[0].recommendation.priority").value(1))
                .andExpect(jsonPath("$.data.items[0].recommendation.status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].recommendation.effect.contributionAmount").value(1_000_000))
                .andExpect(jsonPath("$.data.items[0].recommendation.effect.currentForecastDate").isEmpty())
                .andExpect(jsonPath("$.data.items[0].recommendation.effect.improvedForecastDate").value("2026-10-13"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).at("/data/items/0/recommendation/recommendationId").asLong();

        mvc.perform(post("/api/v1/recommendations/generate").with(user(owner.toString())).contentType(JSON)
                        .content("{\"diagnosisId\":" + diagnosisId + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(0))
                .andExpect(jsonPath("$.data.reused").value(1))
                .andExpect(jsonPath("$.data.items[0].recommendation.recommendationId").value(id));
        assertThat(recommendations.findAllByUserId(owner)).hasSize(1);
        assertThat(histories.findOwned(owner, id)).hasSize(1);

        mvc.perform(get("/api/v1/recommendations/{id}/reasons", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].code").value("GOAL_SAVING_SHORTFALL"))
                .andExpect(jsonPath("$.data[1].code").value("GOAL_FORECAST_DELAY"))
                .andExpect(jsonPath("$.data[1].evidence.remaining").value(1_000_000));
        mvc.perform(get("/api/v1/recommendations/{id}/effect", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.contributionAmount").value(1_000_000)).andExpect(jsonPath("$.data.assumptions").isEmpty());
        mvc.perform(get("/api/v1/recommendations/{id}/goal-impact", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.goalId").value(goalId))
                .andExpect(jsonPath("$.data.improvedForecastDate").value("2026-10-13"))
                .andExpect(jsonPath("$.data.unavailableReason").isEmpty());
        mvc.perform(get("/api/v1/recommendations/{id}", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.diagnosisId").value(diagnosisId));
    }

    @Test
    void generateRejectsIncompleteMismatchedAndForeignDiagnoses() throws Exception {
        long diagnosisId = diagnose(owner, goalId);
        Long otherGoal = goals.save(Goal.create(owner, "다른 목표", GoalType.SAVING, NOW)).getId();
        mvc.perform(post("/api/v1/recommendations/generate").with(user(owner.toString())).contentType(JSON)
                        .content("{\"diagnosisId\":" + diagnosisId + ",\"goalId\":" + otherGoal + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RECOMMENDATION_GOAL_MISMATCH"));
        mvc.perform(post("/api/v1/recommendations/generate").with(user(other.toString())).contentType(JSON)
                        .content("{\"diagnosisId\":" + diagnosisId + "}"))
                .andExpect(status().isNotFound());
        for (String body : List.of("{}", "{\"diagnosisId\":0}", "{\"diagnosisId\":1,\"goalId\":-1}")) {
            mvc.perform(post("/api/v1/recommendations/generate").with(user(owner.toString())).contentType(JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/recommendations/generate").contentType(JSON).content("{\"diagnosisId\":1}"))
                .andExpect(status().isUnauthorized());
        // 문제 0건 진단(목표 없는 사용자)은 빈 목록 + skipped 비어 있음
        long empty = diagnose(other, null);
        mvc.perform(post("/api/v1/recommendations/generate").with(user(other.toString())).contentType(JSON)
                        .content("{\"diagnosisId\":" + empty + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.skipped").isEmpty()).andExpect(jsonPath("$.data.created").value(0));
    }

    @Test
    void listSummaryPrioritiesAreOwnedFilteredAndScoped() throws Exception {
        long first = generate(diagnose(owner, goalId));
        // 같은 목표의 두 번째 진단 → 새 진단 키 공간이라 새 추천. priorities는 최신 진단만
        given(diagnosisClock.now()).willReturn(NOW.plusDays(1));
        given(clock.now()).willReturn(NOW.plusDays(1));
        long latestDiagnosis = diagnose(owner, goalId);
        long second = generate(latestDiagnosis);
        assertThat(second).isNotEqualTo(first);

        mvc.perform(get("/api/v1/recommendations").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].recommendationId").value(second));
        mvc.perform(get("/api/v1/recommendations?status=PENDING&domain=SAVING").with(user(owner.toString())))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get("/api/v1/recommendations?domain=CASHFLOW").with(user(owner.toString())))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        for (String query : List.of("status=UNKNOWN", "domain=lower", "page=-1", "size=0", "size=101")) {
            mvc.perform(get("/api/v1/recommendations?" + query).with(user(owner.toString()))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/recommendations").with(user(other.toString())))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mvc.perform(get("/api/v1/recommendations/summary").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.byStatus.PENDING").value(2)).andExpect(jsonPath("$.data.byStatus.COMPLETED").value(0))
                .andExpect(jsonPath("$.data.byDomain.SAVING").value(2))
                .andExpect(jsonPath("$.data.expectedContributionAmount").value(2_000_000))
                .andExpect(jsonPath("$.data.expectedMonthlyCostSaving").value(0))
                // 발견 수치는 최신 진단 한 건만 본다. 675: 목표 위험(GOAL) 문제와 그 추천(저축 늘리기)은 발견 목록에 없으므로 뺀다
                .andExpect(jsonPath("$.data.discovery.diagnosisId").value(latestDiagnosis))
                .andExpect(jsonPath("$.data.discovery.issueCount").value(0))
                .andExpect(jsonPath("$.data.discovery.openRecommendationCount").value(0))
                .andExpect(jsonPath("$.data.discovery.notes").value(org.hamcrest.Matchers.hasItems("GOAL_ISSUES_EXCLUDED", "NO_OPEN_RECOMMENDATION")))
                .andExpect(jsonPath("$.data.discovery.expectedAnnualCostSaving").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/v1/recommendations/summary").with(user(other.toString())))
                .andExpect(jsonPath("$.data.total").value(0)).andExpect(jsonPath("$.data.byStatus.PENDING").value(0))
                .andExpect(jsonPath("$.data.discovery").value(org.hamcrest.Matchers.nullValue()));

        mvc.perform(get("/api/v1/recommendations/priorities").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.diagnosisId").value(latestDiagnosis))
                .andExpect(jsonPath("$.data.recommendations.length()").value(1))
                .andExpect(jsonPath("$.data.recommendations[0].recommendationId").value(second));
        mvc.perform(get("/api/v1/recommendations/priorities?goalId=" + goalId).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.recommendations[0].recommendationId").value(second));
        mvc.perform(get("/api/v1/recommendations/priorities?goalId=" + goalId).with(user(other.toString())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/recommendations/priorities").with(user(other.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.diagnosisId").isEmpty())
                .andExpect(jsonPath("$.data.recommendations").isEmpty());

        for (String suffix : List.of("", "/reasons", "/effect", "/goal-impact")) {
            mvc.perform(get("/api/v1/recommendations/" + first + suffix).with(user(other.toString()))).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/recommendations/" + first + suffix)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/v1/recommendations/abc").with(user(owner.toString()))).andExpect(status().isBadRequest());
    }

    @Test
    void statusTransitionsFollowTableAndRecordHistory() throws Exception {
        long id = generate(diagnose(owner, goalId));
        String base = "/api/v1/recommendations/" + id;
        mvc.perform(post(base + "/complete").with(user(owner.toString()))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_STATUS_TRANSITION_NOT_ALLOWED"));
        mvc.perform(post(base + "/defer").with(user(owner.toString())).contentType(JSON).content("{\"memo\":\"다음 달에\",\"reason\":\"  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DEFERRED"))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
        mvc.perform(post(base + "/defer").with(user(owner.toString()))).andExpect(status().isConflict());
        mvc.perform(post(base + "/accept").with(user(other.toString()))).andExpect(status().isNotFound());
        mvc.perform(post(base + "/accept")).andExpect(status().isUnauthorized());
        mvc.perform(post(base + "/accept").with(user(owner.toString())).contentType(JSON).content("{\"memo\":\"" + "가".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(base + "/accept").with(user(owner.toString()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        mvc.perform(post(base + "/complete").with(user(owner.toString())).contentType(JSON).content("{\"reason\":\"적금 가입\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        for (String action : List.of("accept", "reject", "complete", "defer")) {
            mvc.perform(post(base + "/" + action).with(user(owner.toString()))).andExpect(status().isConflict());
        }
        // 이력: PENDING(생성) → DEFERRED → ACCEPTED → COMPLETED, 공백 reason은 null
        var history = histories.findOwned(owner, id);
        assertThat(history).hasSize(4);
        assertThat(history.get(0).getStatus().name()).isEqualTo("COMPLETED");
        assertThat(history.get(0).getReason()).isEqualTo("적금 가입");
        assertThat(history.get(2).getMemo()).isEqualTo("다음 달에");
        assertThat(history.get(2).getReason()).isNull();
        // 효과·priority는 상태와 무관하게 불변
        mvc.perform(get(base + "/effect").with(user(owner.toString()))).andExpect(jsonPath("$.data.contributionAmount").value(1_000_000));
    }

    @Test
    void historyIsPeriodBoundedOwnedAndOrdered() throws Exception {
        long id = generate(diagnose(owner, goalId));
        given(clock.now()).willReturn(NOW.plusDays(2));
        mvc.perform(post("/api/v1/recommendations/{id}/reject", id).with(user(owner.toString())).contentType(JSON)
                .content("{\"reason\":\"불필요\"}")).andExpect(status().isOk());

        mvc.perform(get("/api/v1/recommendations/history").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.data.content[0].reason").value("불필요"))
                .andExpect(jsonPath("$.data.content[0].title").isNotEmpty())
                .andExpect(jsonPath("$.data.content[1].status").value("PENDING"));
        mvc.perform(get("/api/v1/recommendations/history?startDate=2026-09-14&endDate=2026-09-15").with(user(owner.toString())))
                .andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.content[0].status").value("REJECTED"));
        mvc.perform(get("/api/v1/recommendations/history?startDate=2026-09-13&endDate=2026-09-13").with(user(owner.toString())))
                .andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.content[0].status").value("PENDING"));
        mvc.perform(get("/api/v1/recommendations/history?startDate=2026-09-15&endDate=2026-09-14").with(user(owner.toString())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/recommendations/history?startDate=2025-01-01&endDate=2026-09-15").with(user(owner.toString())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/recommendations/history?size=101").with(user(owner.toString()))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/recommendations/history").with(user(other.toString())))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/v1/recommendations/history")).andExpect(status().isUnauthorized());
    }

    @Test
    void spendingGoalDiagnosisProducesReduceSpendingWithAnnualEffect() throws Exception {
        Long spendingGoal = goals.save(Goal.create(owner, "식비 줄이기", GoalType.SPENDING, NOW.minusMonths(1))).getId();
        spendingGoals.save(SpendingGoal.create(spendingGoal, 1L)); // 예산 1원 → 어떤 지출도 초과
        long diagnosisId = diagnose(owner, spendingGoal);
        String body = mvc.perform(post("/api/v1/recommendations/generate").with(user(owner.toString())).contentType(JSON)
                        .content("{\"diagnosisId\":" + diagnosisId + "}"))
                .andReturn().getResponse().getContentAsString();
        var data = json.readTree(body).path("data");
        // 거래가 없으면 지출 0 → 초과 0 → 후보 없음(skipped 아님). 진단이 문제를 만들었는지에 따라 분기하되 형식 오류는 없어야 한다
        assertThat(data.path("skipped")).isEmpty();
        for (var item : data.path("items")) {
            assertThat(item.at("/recommendation/type").asText()).isEqualTo("REDUCE_SPENDING");
            assertThat(item.at("/recommendation/effect/annualCostSaving").asLong())
                    .isEqualTo(item.at("/recommendation/effect/monthlyCostSaving").asLong() * 12);
        }
    }

    @Test
    void savingRecommendationCarriesPresetEverywhereAndPresetRunMatchesRecommendedForecast() throws Exception {
        String diagnosis = mvc.perform(post("/api/v1/diagnoses").with(user(owner.toString())).contentType(JSON)
                        .content("{\"goalId\":" + goalId + ",\"forceRefresh\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long diagnosisId = json.readTree(diagnosis).at("/data/diagnosis/diagnosisId").asLong();

        String generated = mvc.perform(post("/api/v1/recommendations/generate").with(user(owner.toString())).contentType(JSON)
                        .content("{\"diagnosisId\":" + diagnosisId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items[0].recommendation.type").value("INCREASE_MONTHLY_SAVING"))
                .andExpect(jsonPath("$.data.items[0].recommendation.effect.contributionAmount").value(1_000_000))
                .andExpect(jsonPath("$.data.items[0].recommendation.effect.improvedForecastDate").value("2026-10-13"))
                .andReturn().getResponse().getContentAsString();
        JsonNode item = json.readTree(generated).at("/data/items/0/recommendation");
        long id = item.path("recommendationId").asLong();
        JsonNode preset = item.path("simulation");
        assertThat(preset.path("available").asBoolean()).isTrue();
        assertThat(preset.path("type").asText()).isEqualTo("SAVING");
        assertThat(preset.path("endpoint").asText()).isEqualTo("/api/v1/simulations/saving");
        assertThat(preset.path("request").path("monthlySavingAmount").asLong()).isEqualTo(1_000_000L);
        assertThat(preset.path("request").path("accountNo").isNull()).isTrue();
        assertThat(preset.path("request").path("durationMonths").isNull()).isTrue();
        assertThat(preset.path("unavailableReason").isMissingNode()).isTrue();
        assertThat(preset.path("request").path("goalId").asLong()).isEqualTo(goalId); // 619: 추천이 겨냥한 목표를 그대로 넘긴다
        assertThat(preset.path("assumptions")).isEmpty();

        // 상세·목록·priorities·상태변경이 같은 프리셋을 싣는다
        for (String path : new String[] {"$.data", "$.data.content[0]", "$.data.recommendations[0]"}) {
            String url = path.equals("$.data") ? "/api/v1/recommendations/" + id
                    : path.startsWith("$.data.content") ? "/api/v1/recommendations" : "/api/v1/recommendations/priorities";
            mvc.perform(get(url).with(user(owner.toString())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(path + ".simulation.available").value(true))
                    .andExpect(jsonPath(path + ".simulation.type").value("SAVING"))
                    .andExpect(jsonPath(path + ".simulation.request.monthlySavingAmount").value(1_000_000));
        }
        mvc.perform(post("/api/v1/recommendations/{id}/defer", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DEFERRED"))
                .andExpect(jsonPath("$.data.simulation.request.monthlySavingAmount").value(1_000_000));
        mvc.perform(get("/api/v1/recommendations/{id}", id).with(user(other.toString()))).andExpect(status().isNotFound());

        // 프리셋 본문을 그대로 실행 → 201. 추천의 improvedForecastDate(진단 기준일 + ceil(1,000,000/1,000,000)개월)와 같은 날짜.
        // 619: 목표가 둘이어도 프리셋의 goalId가 추천이 겨냥한 목표만 바꾸므로 등식이 유지된다(587은 목표 1개에서만 고정)
        Goal another = goals.save(Goal.create(owner, "여행", GoalType.SAVING, NOW.minusMonths(1)));
        savingGoals.save(SavingGoal.create(another.getId(), 2_000_000L, NOW.toLocalDate().plusYears(1)));
        String run = mvc.perform(post(preset.path("endpoint").asText()).with(user(owner.toString())).contentType(JSON)
                        .content(json.writeValueAsString(preset.path("request"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.created").value(true))
                .andExpect(jsonPath("$.data.simulation.type").value("SAVING"))
                .andExpect(jsonPath("$.data.simulation.request.goalId").value(goalId))
                .andExpect(jsonPath("$.data.simulation.after.monthlySaving").value(1_000_000))
                .andExpect(jsonPath("$.data.simulation.goalImpacts.length()").value(2))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].goalId").value(goalId))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].beforeForecastDate").isEmpty())
                .andExpect(jsonPath("$.data.simulation.goalImpacts[1].goalId").value(another.getId()))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[1].afterForecastDate").isEmpty()) // 대상이 아니라 속도 0 그대로
                .andReturn().getResponse().getContentAsString();
        JsonNode simulation = json.readTree(run).at("/data/simulation");
        assertThat(simulation.at("/goalImpacts/0/afterForecastDate").asText())
                .isEqualTo(item.at("/effect/improvedForecastDate").asText());
        assertThat(simulation.at("/request/accountId").isNull()).isTrue();
        assertThat(simulation.at("/after/assumptions")).extracting(JsonNode::asText).doesNotContain("SPLIT_BY_REMAINING");
        assertThat(run).doesNotContain("requestHash");

        // 같은 날 같은 프리셋 재실행은 재사용(멱등)
        mvc.perform(post(preset.path("endpoint").asText()).with(user(owner.toString())).contentType(JSON)
                        .content(json.writeValueAsString(preset.path("request"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(false))
                .andExpect(jsonPath("$.data.simulation.simulationId").value(simulation.path("simulationId").asLong()));
    }
    /**
     * 618: 소비 추천 효과 = 프리셋 실행의 가용자금 증가. 진단은 이번 달 지출(spent·projected)로 초과분을 내고 시뮬레이션 기준선은
     * 직전 3개월 평균 지출을 쓰므로 두 창 모두에 거래가 있어야 하며, 초과분이 평균 지출 이하이고 소득이 있어야 등식이 성립한다.
     */
    @Test
    void spendingRecommendationEffectEqualsPresetRunAvailableFundsGain() throws Exception {
        // 예산 1,000,000. 09-05 지출 1,300,000 → spent 1,300,000 > 예산(OVER_BUDGET), projected = 1,300,000 × 30 / 13 = 3,000,000 → over 2,000,000
        Long spendingGoal = goals.save(Goal.create(owner, "생활비 줄이기", GoalType.SPENDING, NOW.minusMonths(2))).getId();
        spendingGoals.save(SpendingGoal.create(spendingGoal, 1_000_000L));
        expense(LocalDate.of(2026, 9, 5), 1_300_000L);
        // 시뮬레이션 기준선: 6·7·8월 평균 2,400,000 ≥ 감소액 2,000,000. 소득 3,000,000 → 가용자금 600,000
        for (int month = 6; month <= 8; month++) {
            expense(LocalDate.of(2026, month, 20), 2_400_000L);
        }
        FinancialProfile profile = FinancialProfile.empty(owner);
        profile.update(3_000_000L, null, null, null, null);
        profiles.save(profile);

        long diagnosisId = diagnose(owner, spendingGoal);
        mvc.perform(get("/api/v1/diagnoses/{id}/issues?type=SPENDING_GOAL_RISK", diagnosisId).with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        String generated = mvc.perform(post("/api/v1/recommendations/generate").with(user(owner.toString())).contentType(JSON)
                        .content("{\"diagnosisId\":" + diagnosisId + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode item = null;
        for (JsonNode candidate : json.readTree(generated).at("/data/items")) {
            if ("REDUCE_SPENDING".equals(candidate.at("/recommendation/type").asText())) {
                item = candidate.path("recommendation");
            }
        }
        assertThat(item).as("REDUCE_SPENDING 추천").isNotNull();
        long monthlyCostSaving = item.at("/effect/monthlyCostSaving").asLong();
        assertThat(monthlyCostSaving).isEqualTo(2_000_000L);
        JsonNode preset = item.path("simulation");
        assertThat(preset.path("available").asBoolean()).isTrue();
        assertThat(preset.path("type").asText()).isEqualTo("SPENDING");
        assertThat(preset.path("endpoint").asText()).isEqualTo("/api/v1/simulations/spending");
        assertThat(preset.path("request").path("monthlyReduction").asLong()).isEqualTo(monthlyCostSaving);
        assertThat(preset.path("request").path("category").isNull()).isTrue();
        assertThat(preset.path("request").path("durationMonths").isNull()).isTrue();

        // 프리셋 본문 그대로 실행 → 가용자금 증가분 = 지출 감소분 = 추천 효과. 날짜 식이 아니라 기준일이 달라도 금액은 같다
        String run = mvc.perform(post(preset.path("endpoint").asText()).with(user(owner.toString())).contentType(JSON)
                        .content(json.writeValueAsString(preset.path("request"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.type").value("SPENDING"))
                .andExpect(jsonPath("$.data.simulation.before.monthlySpending").value(2_400_000))
                .andExpect(jsonPath("$.data.simulation.before.monthlyAvailableFunds").value(600_000))
                .andReturn().getResponse().getContentAsString();
        JsonNode simulation = json.readTree(run).at("/data/simulation");
        long before = simulation.at("/before/monthlyAvailableFunds").asLong();
        long after = simulation.at("/after/monthlyAvailableFunds").asLong();
        assertThat(after - before).isEqualTo(monthlyCostSaving);
        assertThat(simulation.at("/before/monthlySpending").asLong() - simulation.at("/after/monthlySpending").asLong())
                .isEqualTo(monthlyCostSaving);
    }

    /**
     * 627: 카테고리 소비 증가·고금리 대출 진단 → 추천 3건. 식비 100,000 → 140,000(+40,000), 대출 1,200,000·12%·만기일시 3회(이자 36,000)를
     * 6% 상품 A로 대환하면 18,000 절감(회차당 6,000). 연체 대출은 절감액 없이 비교 권고만. 우선순위: 대환(HIGH 30 + 효과 1) >
     * 연체 대출(HIGH 30) > 식비(MEDIUM 20 + 효과 1 + 난이도 5).
     */
    @Test
    void categorySpendingAndRefinanceRecommendationsCarryPresets() throws Exception {
        Long fresh = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "627")).getId();
        long food = category("FOOD", "식비");
        for (int month = 3; month <= 5; month++) {
            expense(fresh, LocalDate.of(2026, month, 5), 100_000L, food);
        }
        expense(fresh, LocalDate.of(2026, 6, 5), 120_000L, food);
        expense(fresh, LocalDate.of(2026, 7, 5), 140_000L, food);
        expense(fresh, LocalDate.of(2026, 8, 5), 160_000L, food);
        long company = company();
        long own = product(company, "내 신용대출", "PERSONAL_CREDIT");
        long productA = product(company, "A 신용대출", "PERSONAL_CREDIT");
        option(productA, 3, "6", "BULLET");
        long productB = product(company, "B 갈아타기", "CREDIT_REFINANCE");
        option(productB, 12, "8", "BULLET");
        long loanId = loan(fresh, "9900000000011", own, "ACTIVE", 1_200_000L, "12");
        long overdue = loan(fresh, "9900000000012", own, "OVERDUE", 500_000L, "9.5");

        long diagnosisId = diagnose(fresh, null);
        String generated = mvc.perform(post("/api/v1/recommendations/generate").with(user(fresh.toString())).contentType(JSON)
                        .content("{\"diagnosisId\":" + diagnosisId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.created").value(3))
                .andExpect(jsonPath("$.data.skipped").isEmpty())
                .andExpect(jsonPath("$.data.items[0].recommendation.type").value("REFINANCE_LOAN"))
                .andExpect(jsonPath("$.data.items[0].recommendation.priority").value(1))
                .andExpect(jsonPath("$.data.items[1].recommendation.type").value("REFINANCE_LOAN"))
                .andExpect(jsonPath("$.data.items[2].recommendation.type").value("REDUCE_CATEGORY_SPENDING"))
                .andExpect(jsonPath("$.data.items[2].recommendation.priority").value(3))
                .andReturn().getResponse().getContentAsString();
        JsonNode refinance = json.readTree(generated).at("/data/items/0/recommendation");
        assertThat(refinance.at("/domain").asText()).isEqualTo("LOAN");
        assertThat(refinance.at("/title").asText()).isEqualTo("'신용대출' 대환 알아보기");
        assertThat(refinance.at("/effect/interestSaving").asLong()).isEqualTo(18_000L);
        assertThat(refinance.at("/effect/monthlyCostSaving").asLong()).isEqualTo(6_000L);
        assertThat(refinance.at("/effect/annualCostSaving").asLong()).isEqualTo(18_000L);
        assertThat(refinance.at("/simulation/available").asBoolean()).isTrue();
        assertThat(refinance.at("/simulation/type").asText()).isEqualTo("LOAN_REFINANCE");
        assertThat(refinance.at("/simulation/endpoint").asText()).isEqualTo("/api/v1/simulations/loan/refinance");
        assertThat(refinance.at("/simulation/request/targetProductId").asLong()).isEqualTo(productA);
        assertThat(refinance.at("/simulation/request/accountNo").isNull()).isTrue();
        assertThat(refinance.at("/simulation/request/loanId").asLong()).isPositive();
        assertThat(refinance.at("/simulation/request/refinanceAmount").isNull()).isTrue();
        assertThat(refinance.at("/simulation/assumptions").isEmpty()).isTrue();
        assertThat(generated).doesNotContain("9900000000011");
        long refinanceId = refinance.at("/recommendationId").asLong();
        mvc.perform(get("/api/v1/recommendations/{id}/reasons", refinanceId).with(user(fresh.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("LOAN_RATE_ABOVE_MARKET"))
                .andExpect(jsonPath("$.data[1].code").value("REFINANCE_INTEREST_SAVING"))
                .andExpect(jsonPath("$.data[1].evidence.loanId").value(loanId))
                .andExpect(jsonPath("$.data[1].evidence.targetProductId").value(productA))
                .andExpect(jsonPath("$.data[1].evidence.feesIncluded").value(false));

        JsonNode comparison = json.readTree(generated).at("/data/items/1/recommendation");
        assertThat(comparison.at("/title").asText()).isEqualTo("'신용대출' 대환 조건 비교해보기");
        // 628: 같은 LOAN/HIGH_INTEREST_RATE 문제 2건이 각자의 issueId로 구분된다
        String loanIssues = mvc.perform(get("/api/v1/diagnoses/{id}/issues?domain=LOAN", diagnosisId).with(user(fresh.toString())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        java.util.Map<Long, Long> issueByLoan = new java.util.HashMap<>();
        for (JsonNode issue : json.readTree(loanIssues).at("/data/content")) {
            issueByLoan.put(issue.at("/currentValue/loanId").asLong(), issue.at("/issueId").asLong());
        }
        assertThat(refinance.at("/sourceIssueId").asLong()).isEqualTo(issueByLoan.get(loanId));
        assertThat(comparison.at("/sourceIssueId").asLong()).isEqualTo(issueByLoan.get(overdue));
        assertThat(refinance.at("/sourceIssueId").asLong()).isNotEqualTo(comparison.at("/sourceIssueId").asLong());
        assertThat(comparison.at("/effect/interestSaving").isNull()).isTrue();
        assertThat(comparison.at("/simulation/available").asBoolean()).isFalse();
        assertThat(comparison.at("/simulation/unavailableReason").asText()).isEqualTo("REFINANCE_TARGET_MISSING");
        mvc.perform(get("/api/v1/recommendations/{id}/reasons", comparison.at("/recommendationId").asLong()).with(user(fresh.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].code").value("REFINANCE_ESTIMATE_UNAVAILABLE"))
                .andExpect(jsonPath("$.data[1].evidence.loanId").value(overdue))
                .andExpect(jsonPath("$.data[1].evidence.reason").value("LOAN_OVERDUE"));

        JsonNode spending = json.readTree(generated).at("/data/items/2/recommendation");
        assertThat(spending.at("/domain").asText()).isEqualTo("SPENDING");
        assertThat(spending.at("/title").asText()).isEqualTo("식비 지출 줄여보기");
        assertThat(spending.at("/effect/monthlyCostSaving").asLong()).isEqualTo(40_000L);
        assertThat(spending.at("/effect/annualCostSaving").asLong()).isEqualTo(480_000L);
        assertThat(spending.at("/simulation/type").asText()).isEqualTo("SPENDING");
        assertThat(spending.at("/simulation/request/category").asText()).isEqualTo("FOOD");
        assertThat(spending.at("/simulation/request/monthlyReduction").asLong()).isEqualTo(40_000L);
        assertThat(spending.at("/simulation/assumptions").isEmpty()).isTrue();
        // 발견 요약: 문제별 최대 연 절감액의 합(대환 18,000 + 식비 480,000, 비교 추천은 금액 없음), 단축일은 값 없음
        mvc.perform(get("/api/v1/recommendations/summary").with(user(fresh.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.discovery.diagnosisId").value(diagnosisId))
                .andExpect(jsonPath("$.data.discovery.issueCount").value(issueByLoan.size() + 1))
                .andExpect(jsonPath("$.data.discovery.openRecommendationCount").value(3))
                .andExpect(jsonPath("$.data.discovery.expectedAnnualCostSaving").value(498_000))
                .andExpect(jsonPath("$.data.discovery.expectedGoalAdvanceDays").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.discovery.notes[0]").value("ANNUAL_SAVING_BEST_ALTERNATIVE_PER_ISSUE"))
                .andExpect(jsonPath("$.data.discovery.notes[1]").value("GOAL_ADVANCE_UNAVAILABLE"));
        // 목록·상세에서도 같은 프리셋이 실리고, 재요청은 전부 재사용된다
        mvc.perform(get("/api/v1/recommendations/{id}", spending.at("/recommendationId").asLong()).with(user(fresh.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.simulation.request.category").value("FOOD"));
        mvc.perform(get("/api/v1/recommendations?domain=LOAN").with(user(fresh.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(post("/api/v1/recommendations/generate").with(user(fresh.toString())).contentType(JSON)
                        .content("{\"diagnosisId\":" + diagnosisId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reused").value(3));
        // 카테고리 프리셋을 그대로 실행하면 식비 월평균 140,000에서 40,000을 줄인 결과가 된다
        FinancialProfile profile = FinancialProfile.empty(fresh);
        profile.update(3_000_000L, null, null, null, null);
        profiles.save(profile);
        mvc.perform(post(spending.at("/simulation/endpoint").asText()).with(user(fresh.toString())).contentType(JSON)
                        .content(json.writeValueAsString(spending.at("/simulation/request"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.type").value("SPENDING"))
                .andExpect(jsonPath("$.data.simulation.before.monthlySpending").value(140_000))
                .andExpect(jsonPath("$.data.simulation.after.monthlySpending").value(100_000));
    }

    /** 628: 생성·목록·priorities·상세·상태변경 응답 모두 같은 sourceIssueId를 싣고, 그 값은 진단 문제 id와 같다. */
    @Test
    void sourceIssueIdLinksRecommendationToItsDiagnosisIssueInEveryResponse() throws Exception {
        long diagnosisId = diagnose(owner, goalId);
        String issues = mvc.perform(get("/api/v1/diagnoses/{id}/issues", diagnosisId).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andReturn().getResponse().getContentAsString();
        long issueId = json.readTree(issues).at("/data/content/0/issueId").asLong();

        String generated = mvc.perform(post("/api/v1/recommendations/generate").with(user(owner.toString())).contentType(JSON)
                        .content("{\"diagnosisId\":" + diagnosisId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items[0].recommendation.diagnosisId").value(diagnosisId))
                .andExpect(jsonPath("$.data.items[0].recommendation.sourceIssueId").value(issueId))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(generated).at("/data/items/0/recommendation/recommendationId").asLong();

        mvc.perform(get("/api/v1/recommendations").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].sourceIssueId").value(issueId));
        mvc.perform(get("/api/v1/recommendations/priorities").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.recommendations[0].sourceIssueId").value(issueId));
        mvc.perform(get("/api/v1/recommendations/{id}", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sourceIssueId").value(issueId))
                .andExpect(jsonPath("$.data.diagnosisId").value(diagnosisId));
        mvc.perform(post("/api/v1/recommendations/{id}/defer", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sourceIssueId").value(issueId))
                .andExpect(jsonPath("$.data.status").value("DEFERRED"));
        // 재요청은 기존 행을 돌려주며 값이 유지된다
        mvc.perform(post("/api/v1/recommendations/generate").with(user(owner.toString())).contentType(JSON)
                        .content("{\"diagnosisId\":" + diagnosisId + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].recommendation.sourceIssueId").value(issueId));
    }

    private void expense(LocalDate date, long amount) {
        expense(owner, date, amount, null);
    }

    private void expense(Long userId, LocalDate date, long amount, Long categoryId) {
        jdbc.update("""
                INSERT INTO `transaction` (transaction_id, user_id, account_id, card_id, category_id, transaction_date,
                    transaction_time, transaction_type, amount, balance_after, merchant_name, payment_method, spending_type,
                    summary, memo, hidden, source_type, created_at, updated_at)
                VALUES (?, ?, NULL, NULL, ?, ?, NULL, 'EXPENSE', ?, NULL, NULL, 'CARD', 'VARIABLE', 'test', NULL, FALSE, 'SEED', ?, NULL)
                """, UUID.randomUUID().toString(), userId, categoryId, date, amount, NOW);
    }

    private long category(String code, String name) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM transaction_category WHERE code = ?", Integer.class, code) == 0) {
            jdbc.update("INSERT INTO transaction_category (code, name, default_spending_type, active) VALUES (?, ?, 'VARIABLE', TRUE)", code, name);
        }
        return jdbc.queryForObject("SELECT category_id FROM transaction_category WHERE code = ?", Long.class, code);
    }

    private long company() {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM financial_company WHERE company_code = 'TESTBANK'", Integer.class) == 0) {
            jdbc.update("INSERT INTO financial_company (company_code, company_name, sector) VALUES ('TESTBANK', '테스트은행', 'BANK')");
        }
        return jdbc.queryForObject("SELECT company_id FROM financial_company WHERE company_code = 'TESTBANK'", Long.class);
    }

    private long product(long companyId, String name, String loanType) {
        jdbc.update("""
                INSERT INTO financial_product (company_id, source_type, external_product_code, product_type, product_name, join_way,
                    saving_type, loan_type, special_condition, join_member, min_rate, max_rate, max_limit, baseline_performance,
                    max_benefit_limit, description, benefits, synced_at)
                VALUES (?, 'SEED', NULL, 'LOAN', ?, NULL, NULL, ?, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, ?)
                """, companyId, name, loanType, NOW.minusDays(1));
        return jdbc.queryForObject("SELECT MAX(product_id) FROM financial_product", Long.class);
    }

    private void option(long productId, int term, String minRate, String repaymentType) {
        jdbc.update("""
                INSERT INTO financial_product_option (product_id, term, rate, preferred_rate, rate_type, min_rate, max_rate, repayment_type, limit_amount)
                VALUES (?, ?, NULL, NULL, 'FIXED', ?, 10, ?, NULL)
                """, productId, term, new java.math.BigDecimal(minRate), repaymentType);
    }

    /** 만기일시 12개월 약정, 다음 상환일 2026-10-01, 만기 2026-12-01 → 기준일 이후 3회. */
    private long loan(Long userId, String accountNo, long productId, String status, long balance, String rate) {
        jdbc.update("""
                INSERT INTO loan (user_id, withdrawal_account_id, withdrawal_account_type, financial_product_id, institution_code,
                    institution_name, account_no_hash, account_no_masked, account_name, loan_type, repayment_type, status, loan_period,
                    start_date, maturity_date, original_principal, balance, interest_rate, repayment_day, next_repayment_date, created_at, updated_at)
                VALUES (?, 1, 'DEMAND_DEPOSIT', ?, 'TESTBANK', '테스트은행', ?, '9900-***-**001', '신용대출', 'CREDIT', 'BULLET', ?, 12,
                    ?, ?, ?, ?, ?, 1, ?, ?, NULL)
                """, userId, productId, hasher.hash(accountNo), status, LocalDate.of(2025, 12, 1), LocalDate.of(2026, 12, 1), balance, balance,
                new java.math.BigDecimal(rate), LocalDate.of(2026, 10, 1), NOW);
        return jdbc.queryForObject("SELECT loan_id FROM loan WHERE user_id = ? AND account_no_hash = ?", Long.class, userId, hasher.hash(accountNo));
    }
}
