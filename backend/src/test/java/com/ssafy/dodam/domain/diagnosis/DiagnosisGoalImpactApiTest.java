package com.ssafy.dodam.domain.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisStatus;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.diagnosis.service.DiagnosisClock;
import com.ssafy.dodam.domain.diagnosis.service.DiagnosisStore;
import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalStatus;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.entity.SavingGoal;
import com.ssafy.dodam.domain.goal.entity.SpendingGoal;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.goal.repository.SavingGoalRepository;
import com.ssafy.dodam.domain.goal.repository.SpendingGoalRepository;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 546 goal-impact·changes. 저장된 스냅샷만 읽는지, 비교 키가 issueId에 의존하지 않는지를 실제 저장·HTTP로 확인한다. */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:diagnosis546;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class DiagnosisGoalImpactApiTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 13, 12, 0);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired GoalRepository goals;
    @Autowired SavingGoalRepository savings;
    @Autowired SpendingGoalRepository spendings;
    @Autowired DiagnosisStore store;
    @MockitoBean DiagnosisClock clock;
    private Long owner;

    @BeforeEach
    void setup() {
        given(clock.now()).willReturn(NOW);
        owner = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "목표영향 테스트")).getId();
    }

    @Test
    void goalImpactReadsSnapshotAndFirstDiagnosisIsNotComparable() throws Exception {
        Goal saving = goals.save(Goal.create(owner, "모으기", GoalType.SAVING, NOW.minusMonths(2)));
        savings.save(SavingGoal.create(saving.getId(), 1_000_000L, NOW.toLocalDate().plusMonths(1)));
        Goal spending = goals.save(Goal.create(owner, "줄이기", GoalType.SPENDING, NOW.minusMonths(1)));
        spendings.save(SpendingGoal.create(spending.getId(), 300_000L));
        long id = run(null);

        // 진단 후 목표를 바꿔도 응답은 진단 시점 값이어야 한다.
        savings.save(SavingGoal.create(saving.getId(), 1L, NOW.toLocalDate().plusYears(1)));
        JsonNode impact = json.readTree(mvc.perform(get("/api/v1/diagnoses/{id}/goal-impact", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.goals.length()").value(2))
                .andExpect(jsonPath("$.data.unavailable[0].item").value("FINANCING_COST_DELAY"))
                .andReturn().getResponse().getContentAsString()).at("/data/goals");
        JsonNode savingImpact = impact.get(0).get("type").asText().equals("SAVING") ? impact.get(0) : impact.get(1);
        JsonNode spendingImpact = savingImpact == impact.get(0) ? impact.get(1) : impact.get(0);
        assertThat(savingImpact.get("name").asText()).isEqualTo("모으기");
        assertThat(savingImpact.get("targetAmount").asLong()).isEqualTo(1_000_000L);
        assertThat(savingImpact.get("remainingAmount").asLong()).isEqualTo(1_000_000L);
        assertThat(savingImpact.get("monthlyShortfall").asLong()).isEqualTo(1_000_000L);
        // 연결 계좌가 없어 저축속도 0 → 현재 속도로는 달성 불가 → 예상일 null
        assertThat(savingImpact.get("expectedAchievementDate").isNull()).isTrue();
        assertThat(savingImpact.get("relatedIssues")).hasSize(1);
        assertThat(savingImpact.get("coverage")).isEmpty();
        assertThat(spendingImpact.get("monthlyBudget").asLong()).isEqualTo(300_000L);
        assertThat(spendingImpact.get("overBudgetAmount").asLong()).isZero();
        assertThat(spendingImpact.get("remainingAmount").isNull()).isTrue();

        mvc.perform(get("/api/v1/diagnoses/{id}/changes", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.comparable").value(false))
                .andExpect(jsonPath("$.data.previousDiagnosisId").isEmpty())
                .andExpect(jsonPath("$.data.statusChange.direction").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.issueChanges.added").isEmpty());
    }

    @Test
    void changesUseSameGoalScopeAndStableIssueKeys() throws Exception {
        Goal goal = goals.save(Goal.create(owner, "목표", GoalType.SAVING, NOW.minusMonths(2)));
        savings.save(SavingGoal.create(goal.getId(), 1_000_000L, NOW.toLocalDate().plusMonths(1)));
        long first = run(goal.getId());
        // 사이에 끼어든 전체 진단(goalId=null)은 목표 지정 진단의 비교 대상이 아니다.
        given(clock.now()).willReturn(NOW.plusMinutes(1));
        run(null);
        // 목표를 달성 처리하면 위험 NONE, GOAL 문제가 해소된다. 저축 수치 자체는 그대로다.
        goal.changeStatus(GoalStatus.ACHIEVED, NOW);
        goals.save(goal);
        given(clock.now()).willReturn(NOW.plusMinutes(2));
        long second = run(goal.getId());

        mvc.perform(get("/api/v1/diagnoses/{id}/changes", second).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.comparable").value(true))
                .andExpect(jsonPath("$.data.previousDiagnosisId").value(first))
                .andExpect(jsonPath("$.data.ruleVersionChanged").value(false))
                .andExpect(jsonPath("$.data.statusChange.from").value("RISK"))
                .andExpect(jsonPath("$.data.statusChange.direction").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.issueChanges.resolved.length()").value(1))
                .andExpect(jsonPath("$.data.issueChanges.resolved[0].key").value("GOAL:SAVING_GOAL_RISK:" + goal.getId()))
                .andExpect(jsonPath("$.data.issueChanges.added").isEmpty())
                .andExpect(jsonPath("$.data.goalChanges[?(@.metric=='riskLevel')].previous").value("HIGH"))
                .andExpect(jsonPath("$.data.goalChanges[?(@.metric=='riskLevel')].current").value("NONE"))
                .andExpect(jsonPath("$.data.goalChanges[?(@.metric=='remainingAmount')]").isEmpty());
    }

    @Test
    void cashflowKeyIgnoresRegeneratedRiskIdAndLegacySnapshotIsMarked() throws Exception {
        Diagnosis previous = completed(NOW, cashflowIssue(1L, IssueSeverity.MEDIUM), null);
        ObjectNode legacyGoal = json.createObjectNode().put("goalId", 77).put("type", "SAVING").put("riskLevel", "LOW");
        legacyGoal.putObject("metrics").put("requiredMonthlySaving", 50_000).put("averageMonthlySaving", 20_000);
        legacyGoal.putObject("savingGoalSnapshot").put("targetAmount", 600_000).put("targetDate", "2027-01-01");
        ObjectNode snapshot = json.createObjectNode();
        snapshot.putArray("goals").add(legacyGoal);
        Diagnosis current = completed(NOW.plusMinutes(1), cashflowIssue(2L, IssueSeverity.HIGH), snapshot);

        mvc.perform(get("/api/v1/diagnoses/{id}/changes", current.getDiagnosisId()).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.previousDiagnosisId").value(previous.getDiagnosisId()))
                .andExpect(jsonPath("$.data.issueChanges.changed.length()").value(1))
                .andExpect(jsonPath("$.data.issueChanges.changed[0].previousSeverity").value("MEDIUM"))
                .andExpect(jsonPath("$.data.issueChanges.added").isEmpty())
                .andExpect(jsonPath("$.data.issueChanges.resolved").isEmpty())
                .andExpect(jsonPath("$.data.statusChange.direction").value("WORSENED"));
        mvc.perform(get("/api/v1/diagnoses/{id}/goal-impact", current.getDiagnosisId()).with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goals[0].coverage[0]").value("SNAPSHOT_WITHOUT_FORECAST"))
                .andExpect(jsonPath("$.data.goals[0].targetAmount").value(600_000))
                .andExpect(jsonPath("$.data.goals[0].targetDate").value("2027-01-01"))
                .andExpect(jsonPath("$.data.goals[0].monthlyShortfall").value(30_000))
                .andExpect(jsonPath("$.data.goals[0].remainingAmount").isEmpty());
    }

    @Test
    void ownershipAndAuthenticationAreEnforced() throws Exception {
        Diagnosis diagnosis = completed(NOW, null, null);
        Diagnosis pending = store.start(owner, null, NOW);
        Long other = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "다른 사용자")).getId();
        for (String suffix : List.of("/goal-impact", "/changes")) {
            mvc.perform(get("/api/v1/diagnoses/" + diagnosis.getDiagnosisId() + suffix)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/diagnoses/" + diagnosis.getDiagnosisId() + suffix).with(user(other.toString())))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/diagnoses/" + pending.getDiagnosisId() + suffix).with(user(owner.toString())))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/diagnoses/0" + suffix).with(user(owner.toString()))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/diagnoses/{id}/goal-impact", diagnosis.getDiagnosisId()).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.goals").isEmpty());
    }

    private long run(Long goalId) throws Exception {
        String body = goalId == null ? "{\"forceRefresh\":true}" : "{\"goalId\":" + goalId + ",\"forceRefresh\":true}";
        String response = mvc.perform(post("/api/v1/diagnoses").with(user(owner.toString()))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).at("/data/diagnosis/diagnosisId").asLong();
    }

    private Diagnosis completed(LocalDateTime at, Function<Long, DiagnosisIssue> issue, JsonNode snapshot) {
        Diagnosis diagnosis = store.start(owner, null, at);
        List<DiagnosisIssue> issues = issue == null ? List.of() : List.of(issue.apply(diagnosis.getDiagnosisId()));
        DiagnosisStatus status = issues.isEmpty() ? null
                : issues.get(0).isHighSeverity() ? DiagnosisStatus.RISK : DiagnosisStatus.CAUTION;
        store.complete(diagnosis, issues, status, snapshot == null ? json.createObjectNode() : snapshot, at);
        return diagnosis;
    }

    /** 같은 날짜의 같은 위험이지만 riskId·forecastId는 예측 재실행으로 새로 발급된 상황을 흉내 낸다. */
    private Function<Long, DiagnosisIssue> cashflowIssue(long riskId, IssueSeverity severity) {
        ObjectNode evidence = json.createObjectNode().put("ruleKey", "CASHFLOW_BALANCE_SHORTAGE")
                .put("riskDate", "2026-09-20").put("targetId", riskId).put("forecastId", riskId * 10);
        return diagnosisId -> DiagnosisIssue.ofRule(diagnosisId, "CASHFLOW", "BALANCE_SHORTAGE", severity,
                "부족", "근거", null, null, evidence, NOW);
    }
}
