package com.ssafy.dodam.domain.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisStatus;
import com.ssafy.dodam.domain.diagnosis.entity.DetectionSource;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import com.ssafy.dodam.domain.diagnosis.service.DiagnosisClock;
import com.ssafy.dodam.domain.diagnosis.service.DiagnosisEvaluator;
import com.ssafy.dodam.domain.diagnosis.service.DiagnosisExecutionStore;
import com.ssafy.dodam.domain.diagnosis.service.DiagnosisStore;
import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.entity.SavingGoal;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.goal.repository.SavingGoalRepository;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:diagnosis544;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class DiagnosisApiTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 13, 12, 0);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired GoalRepository goals;
    @Autowired SavingGoalRepository savings;
    @Autowired DiagnosisRepository diagnoses;
    @Autowired DiagnosisExecutionStore execution;
    @Autowired DiagnosisStore store;
    @MockitoBean DiagnosisClock clock;
    private Long owner;

    @BeforeEach
    void setup() {
        given(clock.now()).willReturn(NOW);
        owner = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "진단 테스트")).getId();
    }

    @Test
    void emptyDataRemainsUnknownAndCacheCanBeRefreshed() throws Exception {
        String body = mvc.perform(post("/api/v1/diagnoses").with(user(owner.toString())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.diagnosis.overallStatus").isEmpty())
                .andExpect(jsonPath("$.data.diagnosis.coverage").isArray()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).at("/data/diagnosis/diagnosisId").asLong();
        mvc.perform(post("/api/v1/diagnoses").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.cached").value(true))
                .andExpect(jsonPath("$.data.diagnosis.diagnosisId").value(id));
        mvc.perform(post("/api/v1/diagnoses").with(user(owner.toString())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"forceRefresh\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.cached").value(false));
        mvc.perform(get("/api/v1/diagnoses/{id}/issues", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/v1/diagnoses").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void goalRunAndSummaryMatchAndPastResultsDoNotChange() throws Exception {
        Goal goal = goals.save(Goal.create(owner, "목표", GoalType.SAVING, NOW.minusMonths(2)));
        savings.save(SavingGoal.create(goal.getId(), 1_000_000L, NOW.toLocalDate().plusMonths(1)));
        String body = mvc.perform(post("/api/v1/diagnoses").with(user(owner.toString())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"goalId\":" + goal.getId() + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.diagnosis.issueCount").value(1))
                .andExpect(jsonPath("$.data.diagnosis.overallStatus").value("RISK"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).at("/data/diagnosis/diagnosisId").asLong();
        mvc.perform(get("/api/v1/diagnoses/{id}/summary", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.byDomain.GOAL").value(1));
        String before = mvc.perform(get("/api/v1/diagnoses/{id}/issues", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        savings.save(SavingGoal.create(goal.getId(), 1L, NOW.toLocalDate().plusYears(10)));
        String after = mvc.perform(get("/api/v1/diagnoses/{id}/issues", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(after).isEqualTo(before);
    }

    @Test
    void ownershipAuthenticationAndValidationAreEnforced() throws Exception {
        Diagnosis diagnosis = store.start(owner, null, NOW);
        store.complete(diagnosis, List.of(), null, json.createObjectNode(), NOW);
        Long other = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "다른 사용자")).getId();
        Goal otherGoal = goals.save(Goal.create(other, "다른 목표", GoalType.SAVING, NOW));
        for (String suffix : List.of("", "/summary", "/issues", "/issues/1")) {
            mvc.perform(get("/api/v1/diagnoses/" + diagnosis.getDiagnosisId() + suffix).with(user(other.toString())))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/diagnoses/" + diagnosis.getDiagnosisId() + suffix)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/diagnoses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/diagnoses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/diagnoses/latest")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/diagnoses").with(user(owner.toString())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"goalId\":" + otherGoal.getId() + "}" )).andExpect(status().isNotFound());
        for (String query : List.of("page=-1", "size=0", "size=101")) {
            mvc.perform(get("/api/v1/diagnoses?" + query).with(user(owner.toString()))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/diagnoses/{id}/issues?severity=UNKNOWN", diagnosis.getDiagnosisId()).with(user(owner.toString())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/diagnoses/{id}/issues?domain=lowercase", diagnosis.getDiagnosisId()).with(user(owner.toString())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/diagnoses/latest").with(user(other.toString()))).andExpect(status().isNotFound());
    }

    @Test
    void incompleteIsHiddenAndFailureAllowsImmediateRetry() throws Exception {
        var admitted = execution.admit(owner, null, false);
        assertThatThrownBy(() -> execution.admit(owner, null, true)).isInstanceOf(BusinessException.class);
        mvc.perform(get("/api/v1/diagnoses/{id}", admitted.diagnosis().getDiagnosisId()).with(user(owner.toString())))
                .andExpect(status().isNotFound());
        execution.abandon(owner, admitted.diagnosis().getDiagnosisId());
        assertThat(execution.admit(owner, null, false).cached()).isFalse();
    }

    @Test
    void expiredWorkerCannotCompleteOrDeleteNewWorker() {
        var old = execution.admit(owner, null, true);
        given(clock.now()).willReturn(NOW.plusMinutes(5));
        var next = execution.admit(owner, null, true);
        var empty = new DiagnosisEvaluator.Evaluation(List.of(), null, json.createObjectNode());
        assertThatThrownBy(() -> execution.complete(owner, old.diagnosis().getDiagnosisId(), empty))
                .isInstanceOf(BusinessException.class);
        execution.abandon(owner, old.diagnosis().getDiagnosisId());
        assertThat(execution.complete(owner, next.diagnosis().getDiagnosisId(), empty).isCompleted()).isTrue();
        execution.abandon(owner, next.diagnosis().getDiagnosisId());
        assertThat(diagnoses.findById(next.diagnosis().getDiagnosisId())).isPresent();
    }

    @Test
    void severityPagingClusterEvidenceAndNestedOwnership() throws Exception {
        Diagnosis d = store.start(owner, null, NOW);
        var low = DiagnosisIssue.ofRule(d.getDiagnosisId(), "SPENDING", "INCREASE", IssueSeverity.LOW,
                "점검", "근거", null, null, null, NOW);
        var high = DiagnosisIssue.ofCluster(d.getDiagnosisId(), "SPENDING", "INCREASE", IssueSeverity.CRITICAL,
                "점검", "근거", null, null, null, DetectionSource.RULE_AND_CLUSTER, "test-model",
                json.createObjectNode().put("sampleSize", 100).put("modelVersion", "test-model"), NOW);
        store.complete(d, List.of(low, high), DiagnosisStatus.RISK, json.createObjectNode(), NOW);
        String body = mvc.perform(get("/api/v1/diagnoses/{id}/issues?size=1", d.getDiagnosisId()).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].clusterContext.sampleSize").value(100))
                .andReturn().getResponse().getContentAsString();
        long issueId = json.readTree(body).at("/data/content/0/issueId").asLong();
        mvc.perform(get("/api/v1/diagnoses/{id}/issues/{issueId}", d.getDiagnosisId(), issueId).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.detectionSource").value("RULE_AND_CLUSTER"));
        Diagnosis another = store.start(owner, null, NOW);
        store.complete(another, List.of(), null, null, NOW);
        mvc.perform(get("/api/v1/diagnoses/{id}/issues/{issueId}", another.getDiagnosisId(), issueId).with(user(owner.toString())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/diagnoses/{id}/issues?domain=UNKNOWN", d.getDiagnosisId()).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void simultaneousAdmissionCreatesOnlyOneExecution() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<Boolean>) () -> {
                start.await();
                try { execution.admit(owner, null, true); return true; }
                catch (BusinessException conflict) { return false; }
            };
            var first = pool.submit(task);
            var second = pool.submit(task);
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }
}
