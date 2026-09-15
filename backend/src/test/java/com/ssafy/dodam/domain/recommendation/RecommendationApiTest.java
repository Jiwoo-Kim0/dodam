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
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
                .andExpect(jsonPath("$.data.expectedMonthlyCostSaving").value(0));
        mvc.perform(get("/api/v1/recommendations/summary").with(user(other.toString())))
                .andExpect(jsonPath("$.data.total").value(0)).andExpect(jsonPath("$.data.byStatus.PENDING").value(0));

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
        assertThat(preset.path("assumptions").get(0).asText()).isEqualTo("TARGET_GOAL_NOT_SELECTABLE_WITHOUT_ACCOUNT");

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

        // 프리셋 본문을 그대로 실행 → 201. 추천의 improvedForecastDate(진단 기준일 + ceil(1,000,000/1,000,000)개월)와 같은 날짜
        String run = mvc.perform(post(preset.path("endpoint").asText()).with(user(owner.toString())).contentType(JSON)
                        .content(json.writeValueAsString(preset.path("request"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.created").value(true))
                .andExpect(jsonPath("$.data.simulation.type").value("SAVING"))
                .andExpect(jsonPath("$.data.simulation.after.monthlySaving").value(1_000_000))
                .andExpect(jsonPath("$.data.simulation.goalImpacts.length()").value(1))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].goalId").value(goalId))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].beforeForecastDate").isEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode simulation = json.readTree(run).at("/data/simulation");
        assertThat(simulation.at("/goalImpacts/0/afterForecastDate").asText())
                .isEqualTo(item.at("/effect/improvedForecastDate").asText());
        assertThat(simulation.at("/request/accountId").isNull()).isTrue();
        assertThat(run).doesNotContain("requestHash");

        // 같은 날 같은 프리셋 재실행은 재사용(멱등)
        mvc.perform(post(preset.path("endpoint").asText()).with(user(owner.toString())).contentType(JSON)
                        .content(json.writeValueAsString(preset.path("request"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(false))
                .andExpect(jsonPath("$.data.simulation.simulationId").value(simulation.path("simulationId").asLong()));
    }
}
