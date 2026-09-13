package com.ssafy.dodam.domain.recommendation.service;

import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationEffectResponse;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationGoalImpactResponse;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationPrioritiesResponse;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationReasonResponse;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationResponse;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationSummaryResponse;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationStatus;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationReasonRepository;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationRepository;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.response.PageResponse;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장된 추천을 그대로 돌려준다. 현재 데이터로 재계산하지 않는다. 타인·없는 추천은 모두 {@code RESOURCE_NOT_FOUND}다.
 */
@Service
@Transactional(readOnly = true)
public class RecommendationQueryService {
    static final String NOT_FOUND_MESSAGE = "개선안을 찾을 수 없습니다.";
    static final String GOAL_NOT_FOUND_MESSAGE = "목표를 찾을 수 없습니다.";
    static final Set<RecommendationStatus> OPEN_STATUSES = EnumSet.of(RecommendationStatus.PENDING, RecommendationStatus.DEFERRED);
    static final Set<RecommendationStatus> EXPECTED_STATUSES = EnumSet.of(RecommendationStatus.PENDING, RecommendationStatus.ACCEPTED);
    private final RecommendationRepository recommendations;
    private final RecommendationReasonRepository reasons;
    private final DiagnosisRepository diagnoses;
    private final GoalRepository goals;

    public RecommendationQueryService(RecommendationRepository recommendations, RecommendationReasonRepository reasons,
            DiagnosisRepository diagnoses, GoalRepository goals) {
        this.recommendations = recommendations;
        this.reasons = reasons;
        this.diagnoses = diagnoses;
        this.goals = goals;
    }

    public PageResponse<RecommendationResponse> list(Long userId, RecommendationStatus status, String domain, int page, int size) {
        return PageResponse.from(recommendations.findOwned(userId, status, domain, PageRequest.of(page, size)),
                RecommendationResponse::from);
    }

    public RecommendationSummaryResponse summary(Long userId) {
        List<Recommendation> all = recommendations.findAllByUserId(userId);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (RecommendationStatus s : RecommendationStatus.values()) {
            byStatus.put(s.name(), 0L);
        }
        Map<String, Long> byDomain = new TreeMap<>();
        long monthly = 0;
        long contribution = 0;
        for (Recommendation r : all) {
            byStatus.merge(r.getStatus().name(), 1L, Long::sum);
            byDomain.merge(r.getDomain(), 1L, Long::sum);
            if (EXPECTED_STATUSES.contains(r.getStatus())) {
                monthly += r.getMonthlyCostSaving() == null ? 0 : r.getMonthlyCostSaving();
                contribution += r.getContributionAmount() == null ? 0 : r.getContributionAmount();
            }
        }
        return new RecommendationSummaryResponse(all.size(), byStatus, byDomain, monthly, contribution);
    }

    /**
     * 최신 완료 진단(goalId가 있으면 그 목표의 최신 완료 진단)의 PENDING·DEFERRED 추천을 priority 순으로.
     * priority는 생성 요청 안의 순위라 다른 진단과 섞어 비교하지 않는다.
     */
    public RecommendationPrioritiesResponse priorities(Long userId, Long goalId) {
        if (goalId != null) {
            goals.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException(GOAL_NOT_FOUND_MESSAGE));
        }
        Optional<Diagnosis> latest = goalId == null
                ? diagnoses.findFirstByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDescDiagnosisIdDesc(userId)
                : diagnoses.findFirstByUserIdAndGoalIdAndCompletedAtIsNotNullOrderByCompletedAtDescDiagnosisIdDesc(userId, goalId);
        if (latest.isEmpty()) {
            return new RecommendationPrioritiesResponse(null, goalId, List.of());
        }
        Long diagnosisId = latest.get().getDiagnosisId();
        List<RecommendationResponse> items = recommendations
                .findAllByUserIdAndDiagnosisIdOrderByCreatedAtDescRecommendationIdDesc(userId, diagnosisId).stream()
                .filter(r -> OPEN_STATUSES.contains(r.getStatus()))
                .sorted(Comparator.comparingInt(Recommendation::getPriority).thenComparing(Recommendation::getRecommendationId))
                .map(RecommendationResponse::from).toList();
        return new RecommendationPrioritiesResponse(diagnosisId, goalId, items);
    }

    public RecommendationResponse detail(Long userId, Long recommendationId) {
        return RecommendationResponse.from(owned(userId, recommendationId));
    }

    public List<RecommendationReasonResponse> reasons(Long userId, Long recommendationId) {
        owned(userId, recommendationId);
        return reasons.findOwned(userId, recommendationId).stream().map(RecommendationReasonResponse::from).toList();
    }

    public RecommendationEffectResponse effect(Long userId, Long recommendationId) {
        return RecommendationEffectResponse.from(owned(userId, recommendationId));
    }

    public RecommendationGoalImpactResponse goalImpact(Long userId, Long recommendationId) {
        return RecommendationGoalImpactResponse.from(owned(userId, recommendationId));
    }

    Recommendation owned(Long userId, Long recommendationId) {
        return recommendations.findByRecommendationIdAndUserId(recommendationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }
}
