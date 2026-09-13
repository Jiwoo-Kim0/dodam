package com.ssafy.dodam.domain.recommendation.service;

import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationCandidate;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationHistory;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationReason;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationHistoryRepository;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationReasonRepository;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationRepository;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationUserLock;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천 후보 집합의 저장 경로.
 *
 * <p>책임은 <b>사용자 잠금 → 재검증 → 중복 검사 → 추천·근거·초기 이력 저장</b>의 트랜잭션 경계다. 후보
 * 수집(계산)은 이 트랜잭션 밖에서 끝내고 결과만 넘긴다. 규칙 실행이 여러 도메인을 읽는 동안 잠금과
 * 커넥션을 붙잡지 않기 위해서다(DiagnosisExecutionStore와 같은 구조).
 *
 * <p><b>중복 방지</b>: 같은 `generationKey`가 이미 있으면 새 행을 만들지 않고 기존 추천을 돌려준다. 기존
 * 추천의 상태(ACCEPTED 등)·근거·이력은 건드리지 않는다. 같은 키인데 domain/type이 다르면 생성기의 키
 * 구성이 잘못된 것이므로 조용히 덮어쓰지 않고 실패시킨다.
 *
 * <p>DB UNIQUE(`uk_recommendation_user_generation_key`)는 잠금을 우회한 호출의 최종 방어선이다. 이 클래스는
 * 잠금 아래에서 먼저 조회하므로 정상 경로에서 UNIQUE 예외를 만나지 않는다. 예외를 잡아 rollback-only
 * 트랜잭션에서 조회를 이어가지 않는다.
 *
 * <p>모든 검증은 첫 INSERT 전에 끝낸다. 후보 하나의 근거 코드가 중복이면 앞선 후보까지 롤백된다.
 */
@Service
public class RecommendationStore {

    private final RecommendationUserLock users;
    private final DiagnosisRepository diagnoses;
    private final GoalRepository goals;
    private final RecommendationRepository recommendations;
    private final RecommendationReasonRepository reasons;
    private final RecommendationHistoryRepository histories;

    public RecommendationStore(
            RecommendationUserLock users,
            DiagnosisRepository diagnoses,
            GoalRepository goals,
            RecommendationRepository recommendations,
            RecommendationReasonRepository reasons,
            RecommendationHistoryRepository histories
    ) {
        this.users = users;
        this.diagnoses = diagnoses;
        this.goals = goals;
        this.recommendations = recommendations;
        this.reasons = reasons;
        this.histories = histories;
    }

    /**
     * 우선순위가 정해진 후보. 점수 계산은 548이 하므로 여기서는 양수인지만 검사한다.
     */
    public record PrioritizedCandidate(RecommendationCandidate candidate, int priority) {
        public PrioritizedCandidate {
            Objects.requireNonNull(candidate, "candidate");
            if (priority <= 0) {
                throw new IllegalArgumentException("priority는 양수여야 합니다: " + priority);
            }
        }
    }

    /**
     * @param goalId {@link RecommendationInputReader}가 확정한 목표. null이면 목표 없는 추천
     */
    public record SaveRequest(Long userId, Long diagnosisId, Long goalId, List<PrioritizedCandidate> candidates) {
        public SaveRequest {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    /** @param created 이번 호출에서 새로 만들어졌으면 true, 같은 키의 기존 추천이면 false */
    public record Saved(Recommendation recommendation, boolean created) { }

    /**
     * 후보를 저장하고 요청 순서대로 결과를 돌려준다. 후보가 비어 있으면 아무것도 저장하지 않고 빈 목록이다.
     *
     * @param now 생성·초기 이력 시각. 호출자가 시계를 주입한다
     */
    @Transactional
    public List<Saved> save(SaveRequest request, LocalDateTime now) {
        Objects.requireNonNull(now, "now");
        validateCandidates(request.candidates());
        lock(request.userId());
        Diagnosis diagnosis = requireCompletedOwnedDiagnosis(request.userId(), request.diagnosisId());
        requireGoalConsistent(request.userId(), diagnosis, request.goalId());

        List<Saved> result = new ArrayList<>(request.candidates().size());
        for (PrioritizedCandidate prioritized : request.candidates()) {
            RecommendationCandidate candidate = prioritized.candidate();
            String key = RecommendationGenerationKey.of(
                    request.userId(), request.diagnosisId(), request.goalId(),
                    candidate.generatorVersion(), candidate.candidateKey());

            Optional<Recommendation> existing = recommendations.findByUserIdAndGenerationKey(request.userId(), key);
            if (existing.isPresent()) {
                requireSameMeaning(existing.get(), candidate);
                result.add(new Saved(existing.get(), false));
                continue;
            }
            result.add(new Saved(insert(request, prioritized, key, now), true));
        }
        return result;
    }

    private Recommendation insert(SaveRequest request, PrioritizedCandidate prioritized, String key, LocalDateTime now) {
        RecommendationCandidate candidate = prioritized.candidate();
        Recommendation saved = recommendations.save(Recommendation.pending(
                request.userId(), request.diagnosisId(), request.goalId(),
                candidate.domain(), candidate.type(), candidate.title(), candidate.description(),
                prioritized.priority(), candidate.effect(), key, now));
        Long id = saved.getRecommendationId();
        reasons.saveAll(candidate.reasons().stream()
                .map(r -> RecommendationReason.of(id, r.code(), r.description(), r.evidence()))
                .toList());
        histories.save(RecommendationHistory.initial(id, now));
        return saved;
    }

    // 요청 안에서 먼저 잡을 수 있는 결함은 잠금·INSERT 전에 잡는다.
    private static void validateCandidates(List<PrioritizedCandidate> candidates) {
        Set<String> keys = new HashSet<>();
        for (PrioritizedCandidate prioritized : candidates) {
            RecommendationCandidate candidate = prioritized.candidate();
            if (!keys.add(candidate.generatorVersion() + " " + candidate.candidateKey())) {
                throw new IllegalArgumentException("요청 안에 같은 후보 키가 있습니다: " + candidate.candidateKey());
            }
            Set<String> codes = new HashSet<>();
            for (RecommendationCandidate.Reason reason : candidate.reasons()) {
                if (!codes.add(reason.code())) {
                    throw new IllegalArgumentException("한 추천 안에 같은 근거 코드가 있습니다: " + reason.code());
                }
            }
        }
    }

    private void lock(Long userId) {
        users.lock(userId).orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
    }

    // Reader가 읽은 뒤 저장 전까지 진단·목표가 바뀌었을 수 있어 잠금 아래에서 다시 확인한다.
    private Diagnosis requireCompletedOwnedDiagnosis(Long userId, Long diagnosisId) {
        Diagnosis diagnosis = diagnoses.findByDiagnosisIdAndUserId(diagnosisId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("진단을 찾을 수 없습니다."));
        if (!diagnosis.isCompleted()) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_NOT_COMPLETED);
        }
        return diagnosis;
    }

    // 요청 goalId는 Reader가 확정한 값이어야 하므로 진단 goalId와 정확히 같아야 한다. 목표 연계 진단에
    // null을 넘기면 목표 없는 키 공간에 같은 추천이 또 만들어지므로 그것도 불일치로 본다.
    private void requireGoalConsistent(Long userId, Diagnosis diagnosis, Long goalId) {
        if (goalId != null) {
            // 존재·소유를 먼저 확인해야 타인 목표에 대해 "불일치"라는 정보를 흘리지 않는다.
            goals.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("목표를 찾을 수 없습니다."));
        }
        if (!Objects.equals(goalId, diagnosis.getGoalId())) {
            throw new BusinessException(ErrorCode.RECOMMENDATION_GOAL_MISMATCH);
        }
    }

    private static void requireSameMeaning(Recommendation existing, RecommendationCandidate candidate) {
        if (!existing.getDomain().equals(candidate.domain()) || !existing.getType().equals(candidate.type())) {
            throw new IllegalStateException("같은 generationKey에 다른 추천이 이미 있습니다: "
                    + existing.getRecommendationId());
        }
    }
}
