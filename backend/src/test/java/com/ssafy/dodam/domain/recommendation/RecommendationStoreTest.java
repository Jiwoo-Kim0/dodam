package com.ssafy.dodam.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisStatus;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisIssueRepository;
import com.ssafy.dodam.domain.diagnosis.service.DiagnosisStore;
import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationCandidate;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationInput;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationHistory;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationStatus;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationHistoryRepository;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationReasonRepository;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationRepository;
import com.ssafy.dodam.domain.recommendation.service.RecommendationInputReader;
import com.ssafy.dodam.domain.recommendation.service.RecommendationStore;
import com.ssafy.dodam.domain.recommendation.service.RecommendationStore.PrioritizedCandidate;
import com.ssafy.dodam.domain.recommendation.service.RecommendationStore.SaveRequest;
import com.ssafy.dodam.domain.recommendation.service.RecommendationStore.Saved;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * 입력 Reader와 저장 경로 테스트.
 *
 * <p>H2 프로필은 Flyway를 끄고 엔티티로 스키마를 만든다. 엔티티에 선언한 UNIQUE는 검증되지만 MySQL
 * CHECK(priority > 0 등)와 복합 FK는 검증되지 않는다. 그 부분은 {@link RecommendationMySqlIntegrationTest}가 맡는다.
 */
@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:recommendation547;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class RecommendationStoreTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 13, 9, 0);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired UserRepository users;
    @Autowired GoalRepository goals;
    @Autowired DiagnosisStore diagnosisStore;
    @Autowired DiagnosisIssueRepository issues;
    @Autowired RecommendationInputReader reader;
    @Autowired RecommendationStore store;
    @Autowired RecommendationRepository recommendations;
    @Autowired RecommendationReasonRepository reasons;
    @Autowired RecommendationHistoryRepository histories;

    Long owner;
    Long other;

    @BeforeEach
    void users() {
        owner = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "추천 소유자")).getId();
        other = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "다른 사용자")).getId();
    }

    // ---- Reader ----

    @Test
    void readerReturnsCompletedDiagnosisWithIssuesInIdOrder() {
        Long diagnosisId = completedDiagnosis(owner, null, 3);

        RecommendationInput input = reader.read(owner, diagnosisId, null);

        assertThat(input.diagnosisId()).isEqualTo(diagnosisId);
        assertThat(input.userId()).isEqualTo(owner);
        assertThat(input.goalId()).isNull();
        assertThat(input.completedAt()).isNotNull();
        assertThat(input.ruleVersion()).isNotBlank();
        assertThat(input.inputSnapshot().get("asOf").asText()).isEqualTo("2026-09-13");
        assertThat(input.issues()).hasSize(3);
        assertThat(input.issues()).extracting(RecommendationInput.Issue::issueId).isSorted();
        assertThat(input.issues().get(0).currentValue().get("amount").asLong()).isEqualTo(150_000L);
    }

    @Test
    void readerRejectsIncompleteDiagnosis() {
        Diagnosis pending = diagnosisStore.start(owner, null, NOW);

        assertThatThrownBy(() -> reader.read(owner, pending.getDiagnosisId(), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.DIAGNOSIS_NOT_COMPLETED);
    }

    /** 타인 진단과 없는 진단은 같은 예외다. 존재 여부를 흘리지 않는다. */
    @Test
    void readerHidesOtherUsersAndMissingDiagnoses() {
        Long diagnosisId = completedDiagnosis(owner, null, 1);

        assertThatThrownBy(() -> reader.read(other, diagnosisId, null)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> reader.read(owner, diagnosisId + 999_999L, null)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void readerInheritsDiagnosisGoalWhenRequestOmitsIt() {
        Long goalId = goal(owner);
        Long diagnosisId = completedDiagnosis(owner, goalId, 1);

        assertThat(reader.read(owner, diagnosisId, null).goalId()).isEqualTo(goalId);
        assertThat(reader.read(owner, diagnosisId, goalId).goalId()).isEqualTo(goalId);
    }

    @Test
    void readerRejectsGoalDifferentFromDiagnosisGoal() {
        Long diagnosed = goal(owner);
        Long another = goal(owner);
        Long diagnosisId = completedDiagnosis(owner, diagnosed, 1);

        assertThatThrownBy(() -> reader.read(owner, diagnosisId, another))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.RECOMMENDATION_GOAL_MISMATCH);
        // 진단에 목표가 없는데 요청에만 있어도 불일치다.
        Long noGoal = completedDiagnosis(owner, null, 1);
        assertThatThrownBy(() -> reader.read(owner, noGoal, diagnosed)).isInstanceOf(BusinessException.class);
    }

    /** 타인 목표는 "불일치"가 아니라 "없음"이다. 불일치로 답하면 목표의 존재를 흘린다. */
    @Test
    void readerHidesOtherUsersGoalAndRejectsDeletedGoal() {
        Long diagnosisId = completedDiagnosis(owner, null, 1);
        Long othersGoal = goal(other);
        assertThatThrownBy(() -> reader.read(owner, diagnosisId, othersGoal)).isInstanceOf(ResourceNotFoundException.class);

        Long deleted = goal(owner);
        Long withDeleted = completedDiagnosis(owner, deleted, 1);
        Goal g = goals.findById(deleted).orElseThrow();
        g.delete(NOW);
        goals.save(g);
        assertThatThrownBy(() -> reader.read(owner, withDeleted, null)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- Store ----

    @Test
    void saveCreatesRecommendationReasonsAndInitialHistoryAtomically() {
        Long diagnosisId = completedDiagnosis(owner, null, 1);

        List<Saved> saved = store.save(new SaveRequest(owner, diagnosisId, null, List.of(
                new PrioritizedCandidate(candidate("CARD:1", "FEE", "LIMIT"), 1),
                new PrioritizedCandidate(candidate("CARD:2", "FEE"), 2))), NOW);

        assertThat(saved).hasSize(2).allMatch(Saved::created);
        Recommendation first = saved.get(0).recommendation();
        assertThat(first.getRecommendationId()).isNotNull();
        assertThat(first.getUserId()).isEqualTo(owner);
        assertThat(first.getDiagnosisId()).isEqualTo(diagnosisId);
        assertThat(first.getStatus()).isEqualTo(RecommendationStatus.PENDING);
        assertThat(first.getPriority()).isEqualTo(1);
        assertThat(first.getGenerationKey()).matches("[0-9a-f]{64}");
        assertThat(first.getCreatedAt()).isEqualTo(NOW);
        // 효과는 미계산이므로 0이 아니라 null이다.
        assertThat(first.getMonthlyCostSaving()).isNull();
        assertThat(first.getAnnualCostSaving()).isNull();

        Long id = first.getRecommendationId();
        assertThat(reasons.findOwned(owner, id)).extracting(r -> r.getCode()).containsExactly("FEE", "LIMIT");
        assertThat(reasons.findOwned(owner, id).get(0).getEvidence().get("code").asText()).isEqualTo("FEE");
        List<RecommendationHistory> history = histories.findOwned(owner, id);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getStatus()).isEqualTo(RecommendationStatus.PENDING);
        assertThat(history.get(0).getChangedAt()).isEqualTo(NOW);
        assertThat(recommendations.findAllByUserIdAndDiagnosisIdOrderByCreatedAtDescRecommendationIdDesc(owner, diagnosisId))
                .hasSize(2);
    }

    /** 같은 요청을 다시 보내면 새 행·새 이력·새 근거를 만들지 않고 기존 추천을 돌려준다. */
    @Test
    void resavingSameCandidatesIsIdempotent() {
        Long diagnosisId = completedDiagnosis(owner, null, 1);
        SaveRequest request = new SaveRequest(owner, diagnosisId, null,
                List.of(new PrioritizedCandidate(candidate("CARD:1", "FEE"), 1)));

        Recommendation first = store.save(request, NOW).get(0).recommendation();
        List<Saved> again = store.save(request, NOW.plusDays(1)).stream().toList();

        assertThat(again).hasSize(1);
        assertThat(again.get(0).created()).isFalse();
        assertThat(again.get(0).recommendation().getRecommendationId()).isEqualTo(first.getRecommendationId());
        assertThat(recommendations.findAllByUserIdAndDiagnosisIdOrderByCreatedAtDescRecommendationIdDesc(owner, diagnosisId))
                .hasSize(1);
        assertThat(histories.findOwned(owner, first.getRecommendationId())).hasSize(1);
        assertThat(reasons.findOwned(owner, first.getRecommendationId())).hasSize(1);
    }

    /** 다른 생성기 버전·다른 진단은 별도 키 공간이므로 새 추천이 된다. */
    @Test
    void differentVersionOrDiagnosisCreatesNewRecommendation() {
        Long diagnosisId = completedDiagnosis(owner, null, 1);
        store.save(new SaveRequest(owner, diagnosisId, null,
                List.of(new PrioritizedCandidate(candidate("CARD:1", "FEE"), 1))), NOW);

        Saved newVersion = store.save(new SaveRequest(owner, diagnosisId, null, List.of(new PrioritizedCandidate(
                candidateOf("test-v2", "CARD:1", "CARD", "SWITCH_CARD", "FEE"), 1))), NOW).get(0);
        Long otherDiagnosis = completedDiagnosis(owner, null, 1);
        Saved newDiagnosis = store.save(new SaveRequest(owner, otherDiagnosis, null,
                List.of(new PrioritizedCandidate(candidate("CARD:1", "FEE"), 1))), NOW).get(0);

        assertThat(newVersion.created()).isTrue();
        assertThat(newDiagnosis.created()).isTrue();
    }

    @Test
    void sameKeyWithDifferentMeaningIsAContractViolation() {
        Long diagnosisId = completedDiagnosis(owner, null, 1);
        store.save(new SaveRequest(owner, diagnosisId, null,
                List.of(new PrioritizedCandidate(candidate("CARD:1", "FEE"), 1))), NOW);

        assertThatThrownBy(() -> store.save(new SaveRequest(owner, diagnosisId, null, List.of(new PrioritizedCandidate(
                candidateOf("test-v1", "CARD:1", "LOAN", "REFINANCE", "FEE"), 1))), NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 두 번째 후보의 근거 코드가 중복이면 첫 후보까지 저장되지 않는다. */
    @Test
    void invalidCandidateRollsBackWholeRequest() {
        Long diagnosisId = completedDiagnosis(owner, null, 1);

        assertThatThrownBy(() -> store.save(new SaveRequest(owner, diagnosisId, null, List.of(
                new PrioritizedCandidate(candidate("CARD:1", "FEE"), 1),
                new PrioritizedCandidate(candidate("CARD:2", "FEE", "FEE"), 2))), NOW))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(recommendations.findAllByUserIdAndDiagnosisIdOrderByCreatedAtDescRecommendationIdDesc(owner, diagnosisId))
                .isEmpty();
    }

    @Test
    void duplicateCandidateKeysInOneRequestAreRejected() {
        Long diagnosisId = completedDiagnosis(owner, null, 1);

        assertThatThrownBy(() -> store.save(new SaveRequest(owner, diagnosisId, null, List.of(
                new PrioritizedCandidate(candidate("CARD:1", "FEE"), 1),
                new PrioritizedCandidate(candidate("CARD:1", "LIMIT"), 2))), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 628: 후보의 sourceIssueId는 그대로 저장되고, 이 진단의 본인 문제가 아니면 첫 INSERT 전에 실패한다. */
    @Test
    void saveKeepsSourceIssueAndRejectsIssuesOutsideTheDiagnosis() {
        Long diagnosisId = completedDiagnosis(owner, null, 2);
        List<Long> issueIds = issues.findAllOwned(owner, diagnosisId).stream().map(DiagnosisIssue::getIssueId).toList();

        List<Saved> saved = store.save(new SaveRequest(owner, diagnosisId, null, List.of(
                new PrioritizedCandidate(candidateFor(issueIds.get(1), "CARD:1", "FEE"), 1),
                new PrioritizedCandidate(candidate("CARD:2", "FEE"), 2))), NOW);
        assertThat(saved.get(0).recommendation().getSourceIssueId()).isEqualTo(issueIds.get(1));
        assertThat(saved.get(1).recommendation().getSourceIssueId()).isNull();
        assertThat(recommendations.findById(saved.get(0).recommendation().getRecommendationId()).orElseThrow().getSourceIssueId())
                .isEqualTo(issueIds.get(1));

        // 같은 사용자의 다른 진단 문제 → 거부, 앞선 후보까지 저장되지 않는다
        Long otherDiagnosis = completedDiagnosis(owner, null, 1);
        Long foreignIssue = issues.findAllOwned(owner, otherDiagnosis).get(0).getIssueId();
        assertThatThrownBy(() -> store.save(new SaveRequest(owner, diagnosisId, null, List.of(
                new PrioritizedCandidate(candidate("CARD:3", "FEE"), 1),
                new PrioritizedCandidate(candidateFor(foreignIssue, "CARD:4", "FEE"), 2))), NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThat(recommendations.findAllByUserIdAndDiagnosisIdOrderByCreatedAtDescRecommendationIdDesc(owner, diagnosisId))
                .hasSize(2);

        // 타인 진단의 문제 → 소유권 조회에 걸리지 않아 같은 거부
        Long othersDiagnosis = completedDiagnosis(other, null, 1);
        Long othersIssue = issues.findAllOwned(other, othersDiagnosis).get(0).getIssueId();
        assertThatThrownBy(() -> store.save(new SaveRequest(owner, diagnosisId, null, List.of(
                new PrioritizedCandidate(candidateFor(othersIssue, "CARD:5", "FEE"), 1))), NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Recommendation.pending(owner, 1L, null, 0L, "CARD", "T", "t", "d", 1, null, "0".repeat(64), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storeRejectsNonPositivePriority() {
        assertThatThrownBy(() -> new PrioritizedCandidate(candidate("CARD:1", "FEE"), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storeRevalidatesDiagnosisOwnershipAndCompletion() {
        Long diagnosisId = completedDiagnosis(owner, null, 1);
        Diagnosis pending = diagnosisStore.start(owner, null, NOW);
        var candidates = List.of(new PrioritizedCandidate(candidate("CARD:1", "FEE"), 1));

        assertThatThrownBy(() -> store.save(new SaveRequest(other, diagnosisId, null, candidates), NOW))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> store.save(new SaveRequest(owner, pending.getDiagnosisId(), null, candidates), NOW))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> store.save(new SaveRequest(owner + 999_999L, diagnosisId, null, candidates), NOW))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void storeRejectsGoalInconsistentWithDiagnosis() {
        Long goalId = goal(owner);
        Long another = goal(owner);
        Long diagnosisId = completedDiagnosis(owner, goalId, 1);
        var candidates = List.of(new PrioritizedCandidate(candidate("CARD:1", "FEE"), 1));

        assertThat(store.save(new SaveRequest(owner, diagnosisId, goalId, candidates), NOW).get(0)
                .recommendation().getGoalId()).isEqualTo(goalId);
        assertThatThrownBy(() -> store.save(new SaveRequest(owner, diagnosisId, another, candidates), NOW))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> store.save(new SaveRequest(owner, diagnosisId, goal(other), candidates), NOW))
                .isInstanceOf(ResourceNotFoundException.class);
        // 목표 연계 진단에 goalId를 비워 넘기면 목표 없는 키 공간에 중복이 생기므로 거부한다.
        assertThatThrownBy(() -> store.save(new SaveRequest(owner, diagnosisId, null, candidates), NOW))
                .isInstanceOf(BusinessException.class);
        assertThat(recommendations.findAllByUserIdAndDiagnosisIdOrderByCreatedAtDescRecommendationIdDesc(owner, diagnosisId))
                .hasSize(1);
    }

    @Test
    void emptyCandidateListSavesNothing() {
        Long diagnosisId = completedDiagnosis(owner, null, 0);

        assertThat(store.save(new SaveRequest(owner, diagnosisId, null, List.of()), NOW)).isEmpty();
        assertThat(recommendations.findAllByUserIdAndDiagnosisIdOrderByCreatedAtDescRecommendationIdDesc(owner, diagnosisId))
                .isEmpty();
    }

    /** 잠금을 우회한 직접 저장은 DB UNIQUE가 막는다. */
    @Test
    void databaseUniqueKeyIsTheLastLineOfDefense() {
        Long diagnosisId = completedDiagnosis(owner, null, 1);
        String key = "a".repeat(64);
        recommendations.save(Recommendation.pending(owner, diagnosisId, null, null, "CARD", "SWITCH_CARD", "t", "d", 1, null, key, NOW));

        assertThatThrownBy(() -> recommendations.saveAndFlush(
                Recommendation.pending(owner, diagnosisId, null, null, "CARD", "SWITCH_CARD", "t", "d", 1, null, key, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 다른 사용자는 같은 키를 가질 수 있다(UNIQUE는 user_id와 조합).
        Long othersDiagnosis = completedDiagnosis(other, null, 1);
        recommendations.saveAndFlush(
                Recommendation.pending(other, othersDiagnosis, null, null, "CARD", "SWITCH_CARD", "t", "d", 1, null, key, NOW));
    }

    // ---- Entity validation ----

    @Test
    void entityRejectsInvalidValuesBeforeReachingTheDatabase() {
        String key = "b".repeat(64);
        assertThatThrownBy(() -> Recommendation.pending(owner, 1L, null, null, "CARD", "T", "t", "d", 0, null, key, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Recommendation.pending(owner, 1L, null, null, "CARD", "T", "t", "d", 1, null, "short", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Recommendation.pending(owner, 1L, null, null, "", "T", "t", "d", 1, null, key, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Recommendation.Effect(-1L, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecommendationCandidate.Reason(" ", "d", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecommendationHistory.of(1L, RecommendationStatus.ACCEPTED, "x".repeat(501), null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- fixtures ----

    private Long goal(Long userId) {
        return goals.save(Goal.create(userId, "목표", GoalType.SAVING, NOW)).getId();
    }

    private Long completedDiagnosis(Long userId, Long goalId, int issueCount) {
        Diagnosis diagnosis = diagnosisStore.start(userId, goalId, NOW);
        Long id = diagnosis.getDiagnosisId();
        List<DiagnosisIssue> issues = java.util.stream.IntStream.range(0, issueCount)
                .mapToObj(i -> DiagnosisIssue.ofRule(id, "CARD", "HIGH_CARD_FEE", IssueSeverity.HIGH, "t" + i, "d",
                        json("{\"amount\":150000}"), json("{\"amount\":50000}"), null, NOW))
                .toList();
        diagnosisStore.complete(diagnosis, issues,
                issueCount == 0 ? DiagnosisStatus.HEALTHY : DiagnosisStatus.CAUTION,
                json("{\"asOf\":\"2026-09-13\"}"), NOW.plusMinutes(1));
        return id;
    }

    private static RecommendationCandidate candidate(String key, String... reasonCodes) {
        return candidateOf("test-v1", key, "CARD", "SWITCH_CARD", reasonCodes);
    }

    private static RecommendationCandidate candidateFor(Long issueId, String key, String... reasonCodes) {
        RecommendationCandidate c = candidate(key, reasonCodes);
        return new RecommendationCandidate(c.generatorVersion(), c.candidateKey(), issueId, c.domain(), c.type(), c.title(),
                c.description(), c.reasons(), c.effect());
    }

    private static RecommendationCandidate candidateOf(String version, String key, String domain, String type, String... reasonCodes) {
        List<RecommendationCandidate.Reason> rs = java.util.Arrays.stream(reasonCodes)
                .map(code -> new RecommendationCandidate.Reason(code, "근거 " + code, json("{\"code\":\"" + code + "\"}")))
                .toList();
        return new RecommendationCandidate(version, key, null, domain, type, "제목", "설명", rs, null);
    }

    private static JsonNode json(String text) {
        try {
            return JSON.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
