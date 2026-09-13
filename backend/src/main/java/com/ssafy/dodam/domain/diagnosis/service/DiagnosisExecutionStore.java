package com.ssafy.dodam.domain.diagnosis.service;

import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisIssueRepository;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisUserLock;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 접수와 완료에서만 사용자 행을 잠근다. 계산은 이 트랜잭션 밖에서 실행한다. */
@Service
public class DiagnosisExecutionStore {
    static final Duration LEASE = Duration.ofMinutes(5);
    private final DiagnosisUserLock users;
    private final DiagnosisRepository diagnoses;
    private final DiagnosisIssueRepository issues;
    private final GoalRepository goals;
    private final DiagnosisStore store;
    private final DiagnosisClock clock;

    public DiagnosisExecutionStore(DiagnosisUserLock users, DiagnosisRepository diagnoses,
            DiagnosisIssueRepository issues, GoalRepository goals, DiagnosisStore store, DiagnosisClock clock) {
        this.users = users;
        this.diagnoses = diagnoses;
        this.issues = issues;
        this.goals = goals;
        this.store = store;
        this.clock = clock;
    }

    @Transactional
    public Admission admit(Long userId, Long goalId, boolean forceRefresh) {
        lock(userId);
        LocalDateTime now = clock.now();
        if (goalId != null) {
            goals.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("목표를 찾을 수 없습니다."));
        }
        if (diagnoses.findFirstByUserIdAndCompletedAtIsNullAndCreatedAtAfterOrderByCreatedAtDesc(
                userId, now.minus(LEASE)).isPresent()) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_IN_PROGRESS);
        }
        if (!forceRefresh) {
            var cached = diagnoses.findFirstByUserIdAndGoalIdAndRuleVersionAndCompletedAtIsNotNullOrderByCompletedAtDescDiagnosisIdDesc(
                    userId, goalId, DiagnosisRuleVersion.CURRENT)
                    .filter(d -> d.getCompletedAt().toLocalDate().equals(now.toLocalDate()));
            if (cached.isPresent()) {
                return new Admission(cached.get(), true);
            }
        }
        return new Admission(store.start(userId, goalId, now), false);
    }

    @Transactional
    public Diagnosis complete(Long userId, Long diagnosisId, DiagnosisEvaluator.Evaluation result) {
        lock(userId);
        LocalDateTime now = clock.now();
        Diagnosis diagnosis = diagnoses.findByDiagnosisIdAndUserId(diagnosisId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DIAGNOSIS_EXPIRED));
        if (diagnosis.isCompleted() || !diagnosis.getCreatedAt().plus(LEASE).isAfter(now)
                || diagnoses.existsByUserIdAndDiagnosisIdGreaterThan(userId, diagnosisId)) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_EXPIRED);
        }
        store.complete(diagnosis, result.issues(), result.status(), result.snapshot(), now);
        return diagnosis;
    }

    /** 실패한 미완료 접수만 제거해 즉시 재시도할 수 있게 한다. 완료 결과는 삭제하지 않는다. */
    @Transactional
    public void abandon(Long userId, Long diagnosisId) {
        lock(userId);
        diagnoses.findByDiagnosisIdAndUserId(diagnosisId, userId).filter(d -> !d.isCompleted()).ifPresent(d -> {
            issues.deleteByDiagnosisId(diagnosisId);
            diagnoses.delete(d);
        });
    }

    private void lock(Long userId) {
        users.lock(userId).orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
    }

    public record Admission(Diagnosis diagnosis, boolean cached) { }
}
