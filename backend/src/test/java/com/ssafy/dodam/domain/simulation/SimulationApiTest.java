package com.ssafy.dodam.domain.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalAccount;
import com.ssafy.dodam.domain.goal.entity.GoalStatus;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.entity.SavingGoal;
import com.ssafy.dodam.domain.goal.repository.GoalAccountRepository;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.goal.repository.SavingGoalRepository;
import com.ssafy.dodam.domain.goal.service.GoalClock;
import com.ssafy.dodam.domain.simulation.entity.Simulation;
import com.ssafy.dodam.domain.simulation.repository.SimulationGoalImpactRepository;
import com.ssafy.dodam.domain.simulation.repository.SimulationRepository;
import com.ssafy.dodam.domain.simulation.repository.SimulationScenarioRepository;
import com.ssafy.dodam.domain.simulation.service.SimulationClock;
import com.ssafy.dodam.domain.user.entity.FinancialProfile;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.entity.UserRole;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.global.security.AuthPrincipal;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 실행·조회·삭제 API. 기준선은 계좌·거래·목표를 직접 넣어 만들고, 예상 달성일은 Goal API 응답과 대조한다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:simulation550;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class SimulationApiTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 13, 12, 0);
    private static final String ACCOUNT_NO = "1234567890123456";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired FinancialProfileRepository profiles;
    @Autowired GoalRepository goals;
    @Autowired SavingGoalRepository savingGoals;
    @Autowired GoalAccountRepository goalAccounts;
    @Autowired SimulationRepository simulations;
    @Autowired SimulationScenarioRepository scenarios;
    @Autowired SimulationGoalImpactRepository impacts;
    @Autowired SensitiveNumberHasher hasher;
    @MockitoBean SimulationClock clock;
    @MockitoBean GoalClock goalClock;
    private Long owner;
    private Long other;
    private Long accountId;
    private Long goalId;

    @BeforeEach
    void setup() {
        given(clock.now()).willReturn(NOW);
        given(goalClock.now()).willReturn(NOW);
        given(goalClock.today()).willReturn(NOW.toLocalDate());
        owner = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "시뮬레이션")).getId();
        other = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "다른 사용자")).getId();
        FinancialProfile profile = FinancialProfile.empty(owner);
        profile.update(3_000_000L, null, null, null, null);
        profiles.save(profile);
        jdbc.update("DELETE FROM `transaction` WHERE user_id = ?", owner);
        jdbc.update("DELETE FROM simulation_goal_impact");
        jdbc.update("DELETE FROM simulation_scenario");
        jdbc.update("DELETE FROM simulation");
        // 직전 3개월(6·7·8월)에 월 600,000 = FOOD 200,000 + 미분류 400,000. 9월(진행 중) 거래는 기준선에서 제외돼야 한다
        if (jdbc.queryForObject("SELECT COUNT(*) FROM transaction_category WHERE code = 'FOOD'", Integer.class) == 0) {
            jdbc.update("INSERT INTO transaction_category (code, name, default_spending_type, active) VALUES ('FOOD', '식비', 'VARIABLE', TRUE)");
        }
        Long food = jdbc.queryForObject("SELECT category_id FROM transaction_category WHERE code = 'FOOD'", Long.class);
        for (int month = 6; month <= 8; month++) {
            expense(LocalDate.of(2026, month, 5), 200_000L, food);
            expense(LocalDate.of(2026, month, 20), 400_000L, null);
        }
        expense(LocalDate.of(2026, 9, 3), 5_000_000L, null);
        accountId = insertAccount(owner, ACCOUNT_NO, 1_000_000L);
        // 3개월 전 생성 → 속도 1,000,000 / 3 = 333,333, 남은 2,000,000 → 6개월 + 2원/333,333 × 31일(올림 1일) = 2027-03-14 (658 일 단위)
        Goal goal = goals.save(Goal.create(owner, "비상금", GoalType.SAVING, NOW.minusMonths(3)));
        goalId = goal.getId();
        savingGoals.save(SavingGoal.create(goalId, 3_000_000L, NOW.toLocalDate().plusYears(1)));
        goalAccounts.save(GoalAccount.link(owner, goalId, accountId, "DEMAND_DEPOSIT"));
    }

    @Test
    void spendingSimulationRunsStoresAndReusesSameDayRequest() throws Exception {
        String body = mvc.perform(post("/api/v1/simulations/spending").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"food\",\"monthlyReduction\":100000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.created").value(true))
                .andExpect(jsonPath("$.data.simulation.type").value("SPENDING"))
                .andExpect(jsonPath("$.data.simulation.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.simulation.request.category").value("FOOD"))
                .andExpect(jsonPath("$.data.simulation.request.requestHash").doesNotExist())
                .andExpect(jsonPath("$.data.simulation.before.monthlySpending").value(600_000))
                .andExpect(jsonPath("$.data.simulation.before.monthlyIncome").value(3_000_000))
                .andExpect(jsonPath("$.data.simulation.after.monthlySpending").value(500_000))
                .andExpect(jsonPath("$.data.simulation.after.assumptions[0]").value("REDUCTION_DIRECTED_TO_SAVING_GOALS"))
                .andExpect(jsonPath("$.data.simulation.cashflow.months.length()").value(12))
                .andExpect(jsonPath("$.data.simulation.scenarios[0].sequenceNo").value(1))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].goalId").value(goalId))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].beforeForecastDate").value("2027-03-14"))
                // 속도 433,333: 4개월(1,733,332) + 266,668/433,333 × 31일 = 19.08 → 20일 → 2027-02-02
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].afterForecastDate").value("2027-02-02"))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].differenceDays").value(-40))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).at("/data/simulation/simulationId").asLong();
        assertThat(scenarios.findAllBySimulationIdOrderBySequenceNoAsc(id)).hasSize(1);
        assertThat(impacts.findAllByUserIdAndSimulationIdOrderByGoalIdAsc(owner, id)).hasSize(1);
        assertThat(simulations.findById(id).orElseThrow().getRequestSnapshot().path("requestHash").asText()).hasSize(64);

        mvc.perform(post("/api/v1/simulations/spending").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"FOOD\",\"monthlyReduction\":100000,\"durationMonths\":12}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(false))
                .andExpect(jsonPath("$.data.simulation.simulationId").value(id));
        mvc.perform(post("/api/v1/simulations/spending").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"FOOD\",\"monthlyReduction\":100000,\"durationMonths\":6}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.simulation.simulationId").value(id + 1));
        given(clock.now()).willReturn(NOW.plusDays(1));
        mvc.perform(post("/api/v1/simulations/spending").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"FOOD\",\"monthlyReduction\":100000}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.created").value(true));
        assertThat(simulations.count()).isEqualTo(3);
    }

    @Test
    void baselineForecastMatchesGoalForecastApi() throws Exception {
        // Goal API는 @AuthenticationPrincipal AuthPrincipal을 쓰므로 실제 주체 객체로 인증한다
        var principal = new AuthPrincipal(owner, 1L, UserRole.USER);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority(UserRole.USER.authority())));
        String forecast = mvc.perform(get("/api/v1/goals/{id}/forecast", goalId).with(authentication(authentication)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode goalApi = json.readTree(forecast).path("data");
        String run = mvc.perform(post("/api/v1/simulations/saving").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monthlySavingAmount\":500000}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode before = json.readTree(run).at("/data/simulation/before/goals/0");
        assertThat(before.path("monthlySaving").asLong()).isEqualTo(goalApi.path("averageMonthlySaving").asLong());
        assertThat(before.path("remaining").asLong()).isEqualTo(goalApi.path("remainingAmount").asLong());
        assertThat(before.path("expectedAchievementDate").asText()).isEqualTo(goalApi.path("expectedAchievementDate").asText());
    }

    @Test
    void savingSimulationResolvesOwnAccountAndNeverEchoesAccountNumber() throws Exception {
        String body = mvc.perform(post("/api/v1/simulations/saving").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNo\":\"" + ACCOUNT_NO + "\",\"monthlySavingAmount\":500000,\"durationMonths\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.request.accountId").value(accountId))
                .andExpect(jsonPath("$.data.simulation.after.monthlySaving").value(500_000))
                .andExpect(jsonPath("$.data.simulation.cashflow.months[0].delta").value(-(500_000 - 333_333)))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].afterForecastDate").value("2027-01-13"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(ACCOUNT_NO).doesNotContain(hasher.hash(ACCOUNT_NO));
        long id = json.readTree(body).at("/data/simulation/simulationId").asLong();
        Simulation saved = simulations.findById(id).orElseThrow();
        assertThat(saved.getRequestSnapshot().toString()).doesNotContain(ACCOUNT_NO);
        assertThat(scenarios.findAllBySimulationIdOrderBySequenceNoAsc(id).get(0).getParameters().toString()).doesNotContain(ACCOUNT_NO);

        mvc.perform(post("/api/v1/simulations/saving").with(user(other.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNo\":\"" + ACCOUNT_NO + "\",\"monthlySavingAmount\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/simulations/saving").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accountNo\":\"9999999999999999\",\"monthlySavingAmount\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void savingSimulationWithGoalIdChangesOnlyThatGoal() throws Exception {
        // 두 번째 목표는 연결 계좌가 없어 accountNo로는 고를 수 없다(619 추천 프리셋 경로)
        Goal second = goals.save(Goal.create(owner, "여행", GoalType.SAVING, NOW.minusMonths(1)));
        savingGoals.save(SavingGoal.create(second.getId(), 1_200_000L, NOW.toLocalDate().plusYears(1)));

        String body = mvc.perform(post("/api/v1/simulations/saving").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalId\":" + second.getId() + ",\"monthlySavingAmount\":100000,\"durationMonths\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.created").value(true))
                .andExpect(jsonPath("$.data.simulation.request.goalId").value(second.getId()))
                .andExpect(jsonPath("$.data.simulation.request.accountId").value(nullValue()))
                // 첫 목표(속도 333,333)는 그대로, 두 번째 목표(속도 0)만 100,000을 받는다
                .andExpect(jsonPath("$.data.simulation.after.monthlySaving").value(433_333))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].goalId").value(goalId))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].differenceDays").value(0))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[1].goalId").value(second.getId()))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[1].beforeForecastDate").value(nullValue()))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[1].afterForecastDate").value("2027-09-13"))
                .andExpect(jsonPath("$.data.simulation.after.assumptions").value(not(hasItem("SPLIT_BY_REMAINING"))))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).at("/data/simulation/simulationId").asLong();

        // 같은 날 같은 goalId 재실행은 재사용, goalId 없는 실행은 지문이 달라 별개 저장
        mvc.perform(post("/api/v1/simulations/saving").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalId\":" + second.getId() + ",\"monthlySavingAmount\":100000,\"durationMonths\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(false))
                .andExpect(jsonPath("$.data.simulation.simulationId").value(id));
        mvc.perform(post("/api/v1/simulations/saving").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monthlySavingAmount\":100000,\"durationMonths\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.request.goalId").value(nullValue()))
                .andExpect(jsonPath("$.data.simulation.after.assumptions").value(hasItem("SPLIT_BY_REMAINING")));
    }

    @Test
    void savingSimulationGoalIdRejectsUnusableGoalsWithoutStoringAnything() throws Exception {
        long before = simulations.count();
        // 타인·없는 목표는 404 — 존재를 흘리지 않는다
        mvc.perform(post("/api/v1/simulations/saving").with(user(other.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"goalId\":" + goalId + ",\"monthlySavingAmount\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/simulations/saving").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"goalId\":999999,\"monthlySavingAmount\":1}"))
                .andExpect(status().isNotFound());
        // accountNo와 동시 지정, 0·음수 goalId는 400
        mvc.perform(post("/api/v1/simulations/saving").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goalId\":" + goalId + ",\"accountNo\":\"" + ACCOUNT_NO + "\",\"monthlySavingAmount\":1}"))
                .andExpect(status().isBadRequest());
        for (String body : List.of("{\"goalId\":0,\"monthlySavingAmount\":1}", "{\"goalId\":-1,\"monthlySavingAmount\":1}",
                "{\"goalId\":" + goalId + ".5,\"monthlySavingAmount\":1}")) {
            mvc.perform(post("/api/v1/simulations/saving").with(user(owner.toString()))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        // 본인 목표라도 SPENDING·비ACTIVE는 400 — 기준선이 ACTIVE 모으기 목표만 읽는다
        Goal spending = goals.save(Goal.create(owner, "외식 줄이기", GoalType.SPENDING, NOW));
        mvc.perform(post("/api/v1/simulations/saving").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"goalId\":" + spending.getId() + ",\"monthlySavingAmount\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        Goal incomplete = goals.save(Goal.create(owner, "상세 누락", GoalType.SAVING, NOW));
        mvc.perform(post("/api/v1/simulations/saving").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"goalId\":" + incomplete.getId() + ",\"monthlySavingAmount\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        incomplete.delete(NOW);
        goals.save(incomplete);
        mvc.perform(post("/api/v1/simulations/saving").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"goalId\":" + incomplete.getId() + ",\"monthlySavingAmount\":1}"))
                .andExpect(status().isNotFound());
        Goal paused = goals.findById(goalId).orElseThrow();
        paused.changeStatus(GoalStatus.PAUSED, NOW);
        goals.save(paused);
        mvc.perform(post("/api/v1/simulations/saving").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"goalId\":" + goalId + ",\"monthlySavingAmount\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(simulations.count()).isEqualTo(before);
    }

    @Test
    void validationRejectsBadInputsWithoutStoringAnything() throws Exception {
        for (String body : List.of("{\"monthlyReduction\":0}", "{\"monthlyReduction\":-1}", "{}",
                "{\"monthlyReduction\":1,\"durationMonths\":0}", "{\"monthlyReduction\":1,\"durationMonths\":121}",
                "{\"monthlyReduction\":600001}", "{\"category\":\"FOOD\",\"monthlyReduction\":200001}",
                "{\"category\":\"NO_SUCH_CATEGORY\",\"monthlyReduction\":1}")) {
            mvc.perform(post("/api/v1/simulations/spending").with(user(owner.toString()))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        for (String body : List.of("{\"monthlySavingAmount\":-1}", "{}", "{\"monthlySavingAmount\":3000001}",
                "{\"accountNo\":\"abc\",\"monthlySavingAmount\":1}")) {
            mvc.perform(post("/api/v1/simulations/saving").with(user(owner.toString()))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/simulations/spending").contentType(MediaType.APPLICATION_JSON).content("{\"monthlyReduction\":1}"))
                .andExpect(status().isUnauthorized());
        assertThat(simulations.count()).isZero();
    }

    @Test
    void queriesAreOwnedFilteredPagedAndSoftDeleted() throws Exception {
        long spendingId = run("/spending", "{\"monthlyReduction\":10000}");
        long savingId = run("/saving", "{\"monthlySavingAmount\":400000}");
        mvc.perform(get("/api/v1/simulations").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].simulationId").value(savingId));
        mvc.perform(get("/api/v1/simulations?type=SPENDING").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].simulationId").value(spendingId));
        mvc.perform(get("/api/v1/simulations?type=LOAN_NEW").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        for (String query : List.of("type=UNKNOWN", "page=-1", "size=0", "size=101")) {
            mvc.perform(get("/api/v1/simulations?" + query).with(user(owner.toString()))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/simulations").with(user(other.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));

        for (String suffix : List.of("", "/comparison", "/cashflow", "/goal-impact", "/assets", "/debts", "/costs")) {
            mvc.perform(get("/api/v1/simulations/" + spendingId + suffix).with(user(owner.toString()))).andExpect(status().isOk());
            mvc.perform(get("/api/v1/simulations/" + spendingId + suffix).with(user(other.toString()))).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/simulations/" + spendingId + suffix)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/v1/simulations/{id}/goal-impact", spendingId).with(user(owner.toString())))
                .andExpect(jsonPath("$.data[0].goalId").value(goalId));
        mvc.perform(get("/api/v1/simulations/{id}/assets", spendingId).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.content[0].after").value(120_000));
        mvc.perform(get("/api/v1/simulations/{id}/assets?page=1&size=1", spendingId).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.content.length()").value(0));
        mvc.perform(get("/api/v1/simulations/{id}/debts", spendingId).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/v1/simulations/{id}/comparison", spendingId).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.items[0].key").value("SCOPE_MONTHLY_SPENDING"));

        mvc.perform(delete("/api/v1/simulations/{id}", spendingId).with(user(other.toString()))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/simulations/{id}", spendingId).with(user(owner.toString()))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/simulations/{id}", spendingId).with(user(owner.toString()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/simulations/{id}", spendingId).with(user(owner.toString()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/simulations").with(user(owner.toString()))).andExpect(jsonPath("$.data.totalElements").value(1));
        assertThat(simulations.findById(spendingId).orElseThrow().isDeleted()).isTrue();
        // 삭제한 요청을 다시 보내면 재사용하지 않고 새로 만든다
        mvc.perform(post("/api/v1/simulations/spending").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monthlyReduction\":10000}"))
                .andExpect(status().isCreated());
    }

    @Test
    void emptyBaselineStillRunsWithCoverage() throws Exception {
        mvc.perform(post("/api/v1/simulations/saving").with(user(other.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monthlySavingAmount\":100000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.goalImpacts").isEmpty())
                .andExpect(jsonPath("$.data.simulation.before.coverage").isArray())
                .andExpect(jsonPath("$.data.simulation.before.coverage", org.hamcrest.Matchers.containsInAnyOrder(
                        "INCOME_UNKNOWN", "SPENDING_NO_TRANSACTIONS", "NO_ACTIVE_SAVING_GOAL")))
                .andExpect(jsonPath("$.data.simulation.before.monthlyAvailableFunds").isEmpty());
    }

    private long run(String path, String body) throws Exception {
        String response = mvc.perform(post("/api/v1/simulations" + path).with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).at("/data/simulation/simulationId").asLong();
    }

    private void expense(LocalDate date, long amount, Long categoryId) {
        jdbc.update("""
                INSERT INTO `transaction` (transaction_id, user_id, account_id, card_id, category_id, transaction_date,
                    transaction_time, transaction_type, amount, balance_after, merchant_name, payment_method, spending_type,
                    summary, memo, hidden, source_type, created_at, updated_at)
                VALUES (?, ?, NULL, NULL, ?, ?, NULL, 'EXPENSE', ?, NULL, NULL, 'CARD', 'VARIABLE', 'test', NULL, FALSE, 'SEED', ?, NULL)
                """, UUID.randomUUID().toString(), owner, categoryId, date, amount, NOW);
    }

    private Long insertAccount(Long userId, String accountNo, long balance) {
        jdbc.update("""
                INSERT INTO account (user_id, bank_code, bank_name, account_no_encrypted, account_no_hash, account_no_masked,
                    account_name, account_type, status, balance, last_transaction_date, currency, source_type, created_at, updated_at)
                VALUES (?, '004', 'KB국민은행', X'00', ?, '1234-**-**3456', '계좌', 'DEMAND_DEPOSIT', 'ACTIVE', ?, NULL, 'KRW', 'SEED', ?, ?)
                """, userId, hasher.hash(accountNo), balance, NOW, NOW);
        return jdbc.queryForObject("SELECT account_id FROM account WHERE user_id = ? AND account_no_hash = ?", Long.class,
                userId, hasher.hash(accountNo));
    }
}
