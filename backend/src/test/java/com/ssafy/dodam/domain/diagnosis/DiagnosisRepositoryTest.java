package com.ssafy.dodam.domain.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisStatus;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisIssueRepository;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import com.ssafy.dodam.domain.diagnosis.service.DiagnosisStore;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 조회 계약과 소유권 격리 테스트.
 *
 * <p>확인하려는 것은 <b>모든 조회에 `userId` 조건이 실제로 걸려 있는가</b>다. `diagnosis_issue`에는
 * `user_id`가 없어 상위 `diagnosis`를 조인해야만 소유권을 확인할 수 있고, 조인을 빠뜨리면 타인의
 * 문제 목록이 그대로 노출된다.
 */
@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:diagnosis543repo;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class DiagnosisRepositoryTest {

    private static final AtomicLong USER_SEQUENCE = new AtomicLong(5_430_500L);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 10, 9, 0);

    @Autowired private DiagnosisStore store;
    @Autowired private DiagnosisRepository diagnosisRepository;
    @Autowired private DiagnosisIssueRepository issueRepository;

    @Test
    void findsOwnDiagnosisAndHidesOthers() {
        Long owner = nextUserId();
        Long other = nextUserId();
        Long diagnosisId = completedDiagnosis(owner, null, NOW).getDiagnosisId();

        assertThat(diagnosisRepository.findByDiagnosisIdAndUserId(diagnosisId, owner)).isPresent();
        // 타인 진단과 없는 진단이 같은 결과여야 존재 여부가 드러나지 않는다.
        assertThat(diagnosisRepository.findByDiagnosisIdAndUserId(diagnosisId, other)).isEmpty();
        assertThat(diagnosisRepository.findByDiagnosisIdAndUserId(-1L, owner)).isEmpty();
    }

    @Test
    void latestPicksTheMostRecentlyCompletedDiagnosis() {
        Long userId = nextUserId();
        completedDiagnosis(userId, null, NOW);
        Diagnosis newer = completedDiagnosis(userId, null, NOW.plusHours(2));
        // 완료되지 않은 진단은 최신 대상이 아니다. 하위행이 없거나 일부만 있는 상태다.
        store.start(userId, null, NOW.plusHours(5));

        Diagnosis latest = diagnosisRepository
                .findFirstByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDescDiagnosisIdDesc(userId)
                .orElseThrow();

        assertThat(latest.getDiagnosisId()).isEqualTo(newer.getDiagnosisId());
    }

    @Test
    void latestByGoalOnlySeesThatGoal() {
        Long userId = nextUserId();
        Diagnosis withGoal = completedDiagnosis(userId, 77L, NOW);
        completedDiagnosis(userId, null, NOW.plusHours(1));

        assertThat(diagnosisRepository
                .findFirstByUserIdAndGoalIdAndCompletedAtIsNotNullOrderByCompletedAtDescDiagnosisIdDesc(
                        userId, 77L)
                .orElseThrow()
                .getDiagnosisId()).isEqualTo(withGoal.getDiagnosisId());

        assertThat(diagnosisRepository
                .findFirstByUserIdAndGoalIdAndCompletedAtIsNotNullOrderByCompletedAtDescDiagnosisIdDesc(
                        userId, 78L)).isEmpty();
    }

    @Test
    void historyIsNewestFirstAndScopedToTheUser() {
        Long owner = nextUserId();
        Long other = nextUserId();
        completedDiagnosis(owner, null, NOW);
        Diagnosis newest = completedDiagnosis(owner, null, NOW.plusHours(3));
        completedDiagnosis(other, null, NOW.plusHours(4));

        Page<Diagnosis> page = diagnosisRepository
                .findByUserIdOrderByCreatedAtDescDiagnosisIdDesc(owner, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).getDiagnosisId()).isEqualTo(newest.getDiagnosisId());
    }

    @Test
    void inProgressLookupIgnoresRunsOlderThanTheCutoff() {
        Long userId = nextUserId();
        store.start(userId, null, NOW);

        assertThat(diagnosisRepository
                .findFirstByUserIdAndCompletedAtIsNullAndCreatedAtAfterOrderByCreatedAtDesc(
                        userId, NOW.minusMinutes(10))).isPresent();
        // 만료된 접수는 버려진 계산으로 본다. 실패 상태 컬럼이 없어 이 방법뿐이다.
        assertThat(diagnosisRepository
                .findFirstByUserIdAndCompletedAtIsNullAndCreatedAtAfterOrderByCreatedAtDesc(
                        userId, NOW.plusMinutes(10))).isEmpty();
    }

    @Test
    void issueQueriesRequireOwnershipThroughTheParentDiagnosis() {
        Long owner = nextUserId();
        Long other = nextUserId();
        Diagnosis diagnosis = completedDiagnosis(owner, null, NOW);
        Long diagnosisId = diagnosis.getDiagnosisId();
        Long issueId = issueRepository.findAllOwned(owner, diagnosisId).get(0).getIssueId();

        assertThat(issueRepository.findAllOwned(other, diagnosisId)).isEmpty();
        assertThat(issueRepository.findOwnedById(other, diagnosisId, issueId)).isEmpty();
        assertThat(issueRepository.findOwnedById(owner, diagnosisId, issueId)).isPresent();
        assertThat(issueRepository
                .findOwned(other, diagnosisId, null, null, null, PageRequest.of(0, 20))
                .getTotalElements()).isZero();
    }

    @Test
    void issueFiltersAreOptionalAndCombine() {
        Long userId = nextUserId();
        Long diagnosisId = completedDiagnosis(userId, null, NOW).getDiagnosisId();

        assertThat(issueRepository
                .findOwned(userId, diagnosisId, null, null, null, PageRequest.of(0, 20))
                .getTotalElements()).isEqualTo(2);
        assertThat(issueRepository
                .findOwned(userId, diagnosisId, "SPENDING", null, null, PageRequest.of(0, 20))
                .getTotalElements()).isEqualTo(1);
        assertThat(issueRepository
                .findOwned(userId, diagnosisId, null, null, IssueSeverity.CRITICAL, PageRequest.of(0, 20))
                .getTotalElements()).isEqualTo(1);
        assertThat(issueRepository
                .findOwned(userId, diagnosisId, "SPENDING", "OVERSPENDING", IssueSeverity.HIGH,
                        PageRequest.of(0, 20))
                .getTotalElements()).isEqualTo(1);
        // 형식이 맞아도 없는 값은 빈 결과다.
        assertThat(issueRepository
                .findOwned(userId, diagnosisId, "UNKNOWN", null, null, PageRequest.of(0, 20))
                .getTotalElements()).isZero();
    }

    private Long nextUserId() {
        return USER_SEQUENCE.incrementAndGet();
    }

    /** 문제 2건(SPENDING/HIGH, CASHFLOW/CRITICAL)을 가진 완료 진단을 만든다. */
    private Diagnosis completedDiagnosis(Long userId, Long goalId, LocalDateTime startedAt) {
        Diagnosis diagnosis = store.start(userId, goalId, startedAt);
        Long diagnosisId = diagnosis.getDiagnosisId();
        store.complete(
                diagnosis,
                List.of(
                        DiagnosisIssue.ofRule(diagnosisId, "SPENDING", "OVERSPENDING",
                                IssueSeverity.HIGH, "제목", "설명", null, null, null, startedAt),
                        DiagnosisIssue.ofRule(diagnosisId, "CASHFLOW", "BALANCE_SHORTAGE",
                                IssueSeverity.CRITICAL, "제목", "설명", null, null, null, startedAt)),
                DiagnosisStatus.RISK,
                null,
                startedAt.plusMinutes(1));
        return diagnosis;
    }
}
