package com.ssafy.dodam.domain.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.diagnosis.entity.DetectionSource;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisStatus;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisIssueRepository;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import com.ssafy.dodam.domain.diagnosis.service.DiagnosisRuleVersion;
import com.ssafy.dodam.domain.diagnosis.service.DiagnosisStore;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 진단 저장 경로 테스트.
 *
 * <p>H2 프로필은 Flyway를 끄고 엔티티로 스키마를 만들기 때문에 MySQL CHECK와 `goal` 외래키는 여기서
 * 검증되지 않는다. 그 부분은 {@link DiagnosisMySqlIntegrationTest}가 맡는다.
 */
@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:diagnosis543;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class DiagnosisStoreTest {

    private static final AtomicLong USER_SEQUENCE = new AtomicLong(5_430_100L);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 10, 9, 0);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private DiagnosisStore store;
    @Autowired private DiagnosisRepository diagnosisRepository;
    @Autowired private DiagnosisIssueRepository issueRepository;

    @Test
    void startCreatesIncompleteDiagnosisWithZeroCounts() {
        Long userId = nextUserId();
        Diagnosis diagnosis = store.start(userId, null, NOW);

        assertThat(diagnosis.getDiagnosisId()).isNotNull();
        assertThat(diagnosis.getUserId()).isEqualTo(userId);
        assertThat(diagnosis.isCompleted()).isFalse();
        assertThat(diagnosis.getCompletedAt()).isNull();
        assertThat(diagnosis.getIssueCount()).isZero();
        assertThat(diagnosis.getHighSeverityCount()).isZero();
        // 집계값은 완료 시점에만 채워진다.
        assertThat(diagnosis.getOverallStatus()).isNull();
        assertThat(diagnosis.getRuleVersion()).isNull();
    }

    @Test
    void completeCountsIssuesInsteadOfTrustingCallerTotals() {
        Long userId = nextUserId();
        Diagnosis diagnosis = store.start(userId, null, NOW);
        Long diagnosisId = diagnosis.getDiagnosisId();

        store.complete(
                diagnosis,
                List.of(
                        ruleIssue(diagnosisId, "SPENDING", "OVERSPENDING", IssueSeverity.HIGH),
                        ruleIssue(diagnosisId, "CASHFLOW", "BALANCE_SHORTAGE", IssueSeverity.CRITICAL),
                        ruleIssue(diagnosisId, "SAVING", "LOW_SAVING_RATE", IssueSeverity.LOW)),
                DiagnosisStatus.RISK,
                json("{\"asOf\":\"2026-09-10\"}"),
                NOW.plusMinutes(1));

        Diagnosis saved = diagnosisRepository.findById(diagnosisId).orElseThrow();
        assertThat(saved.isCompleted()).isTrue();
        assertThat(saved.getIssueCount()).isEqualTo(3);
        // HIGH와 CRITICAL만 센다.
        assertThat(saved.getHighSeverityCount()).isEqualTo(2);
        assertThat(saved.getOverallStatus()).isEqualTo(DiagnosisStatus.RISK);
        assertThat(saved.getRuleVersion()).isEqualTo(DiagnosisRuleVersion.CURRENT);
        assertThat(saved.getInputSnapshot().get("asOf").asText()).isEqualTo("2026-09-10");
        assertThat(issueRepository.findAllOwned(userId, diagnosisId)).hasSize(3);
    }

    @Test
    void completeWithNoIssuesIsHealthyAndNotAnError() {
        Long userId = nextUserId();
        Diagnosis diagnosis = store.start(userId, null, NOW);

        store.complete(diagnosis, List.of(), DiagnosisStatus.HEALTHY, null, NOW.plusMinutes(1));

        Diagnosis saved = diagnosisRepository.findById(diagnosis.getDiagnosisId()).orElseThrow();
        assertThat(saved.getIssueCount()).isZero();
        assertThat(saved.getOverallStatus()).isEqualTo(DiagnosisStatus.HEALTHY);
        assertThat(saved.isCompleted()).isTrue();
    }

    /** 완료된 진단은 덮어쓰지 않는다. 재진단은 새 행으로 남긴다. */
    @Test
    void completedDiagnosisCannotBeCompletedAgain() {
        Long userId = nextUserId();
        Diagnosis first = store.start(userId, null, NOW);
        Long diagnosisId = first.getDiagnosisId();
        store.complete(
                first,
                List.of(ruleIssue(diagnosisId, "SPENDING", "OVERSPENDING", IssueSeverity.HIGH)),
                DiagnosisStatus.CAUTION, null, NOW.plusMinutes(1));

        assertThatThrownBy(() -> store.complete(
                first, List.of(), DiagnosisStatus.HEALTHY, null, NOW.plusMinutes(2)))
                .isInstanceOf(IllegalStateException.class);

        Diagnosis second = store.start(userId, null, NOW.plusHours(1));
        Long secondId = second.getDiagnosisId();
        store.complete(
                second,
                List.of(ruleIssue(secondId, "CASHFLOW", "BALANCE_SHORTAGE", IssueSeverity.LOW)),
                DiagnosisStatus.CAUTION, null, NOW.plusHours(1).plusMinutes(1));

        // 진단별로 하위행이 섞이지 않는다.
        assertThat(issueRepository.findAllOwned(userId, diagnosisId)).hasSize(1);
        assertThat(issueRepository.findAllOwned(userId, secondId)).hasSize(1);
    }

    @Test
    void discardIssuesRemovesPartialResultsOfAFailedRun() {
        Long userId = nextUserId();
        Diagnosis diagnosis = store.start(userId, null, NOW);
        Long diagnosisId = diagnosis.getDiagnosisId();
        store.complete(
                diagnosis,
                List.of(ruleIssue(diagnosisId, "SPENDING", "OVERSPENDING", IssueSeverity.HIGH)),
                DiagnosisStatus.CAUTION, null, NOW.plusMinutes(1));

        store.discardIssues(diagnosis);

        assertThat(issueRepository.findAllOwned(userId, diagnosisId)).isEmpty();
    }

    @Test
    void rejectsHighSeverityCountGreaterThanIssueCount() {
        Diagnosis diagnosis = Diagnosis.start(nextUserId(), null, NOW);

        assertThatThrownBy(() -> diagnosis.complete(1, 2, DiagnosisStatus.RISK, "v1", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 군집 결과에는 모델 버전이 있어야 재현할 수 있다. */
    @Test
    void rejectsClusterIssueWithoutModelVersion() {
        assertThatThrownBy(() -> DiagnosisIssue.ofCluster(
                1L, "SPENDING", "OVERSPENDING", IssueSeverity.HIGH, "제목", "설명",
                null, null, null, DetectionSource.CLUSTER, null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> DiagnosisIssue.ofCluster(
                1L, "SPENDING", "OVERSPENDING", IssueSeverity.HIGH, "제목", "설명",
                null, null, null, DetectionSource.RULE, "m1", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storesClusterIssueWithModelVersionAndContext() {
        Long userId = nextUserId();
        Diagnosis diagnosis = store.start(userId, null, NOW);
        Long diagnosisId = diagnosis.getDiagnosisId();

        store.complete(
                diagnosis,
                List.of(DiagnosisIssue.ofCluster(
                        diagnosisId, "SPENDING", "PEER_OVERSPENDING", IssueSeverity.MEDIUM,
                        "또래 대비 지출이 많습니다", "설명", json("{\"amount\":100}"), json("{\"p50\":80}"), null,
                        DetectionSource.RULE_AND_CLUSTER, "cluster-v1", json("{\"sampleSize\":120}"), NOW)),
                DiagnosisStatus.CAUTION, null, NOW.plusMinutes(1));

        DiagnosisIssue saved = issueRepository.findAllOwned(userId, diagnosisId).get(0);
        assertThat(saved.getDetectionSource()).isEqualTo(DetectionSource.RULE_AND_CLUSTER);
        assertThat(saved.getModelVersion()).isEqualTo("cluster-v1");
        assertThat(saved.getClusterContext().get("sampleSize").asInt()).isEqualTo(120);
        assertThat(saved.getCurrentValue().get("amount").asInt()).isEqualTo(100);
    }

    /** 다른 진단의 문제가 섞이면 헤더 집계와 실제 저장된 행이 어긋난다. */
    @Test
    void rejectsIssuesBelongingToAnotherDiagnosis() {
        Long userId = nextUserId();
        Diagnosis diagnosis = store.start(userId, null, NOW);
        Long otherDiagnosisId = store.start(userId, null, NOW).getDiagnosisId();

        assertThatThrownBy(() -> store.complete(
                diagnosis,
                List.of(ruleIssue(otherDiagnosisId, "SPENDING", "OVERSPENDING", IssueSeverity.HIGH)),
                DiagnosisStatus.CAUTION, null, NOW.plusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Long nextUserId() {
        return USER_SEQUENCE.incrementAndGet();
    }

    private JsonNode json(String raw) {
        try {
            return JSON.readTree(raw);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private DiagnosisIssue ruleIssue(Long diagnosisId, String domain, String type, IssueSeverity severity) {
        return DiagnosisIssue.ofRule(
                diagnosisId, domain, type, severity, "제목", "설명", null, null, null, NOW);
    }
}
