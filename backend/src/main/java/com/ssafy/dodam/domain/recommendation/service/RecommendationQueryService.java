package com.ssafy.dodam.domain.recommendation.service;

import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationEffectResponse;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationGoalImpactResponse;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationHistoryResponse;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationPrioritiesResponse;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationReasonResponse;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationResponse;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationSimulationPreset;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationSummaryResponse;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationHistory;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationStatus;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationHistoryRepository;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationReasonRepository;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationRepository;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.policy.QueryPeriod;
import com.ssafy.dodam.global.policy.QueryPeriodPolicy;
import com.ssafy.dodam.global.response.PageResponse;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
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
    private final RecommendationHistoryRepository histories;
    private final DiagnosisRepository diagnoses;
    private final GoalRepository goals;
    private final QueryPeriodPolicy periodPolicy;
    private final RecommendationClock clock;
    private final RecommendationSimulationPresetAssembler presets;

    public RecommendationQueryService(RecommendationRepository recommendations, RecommendationReasonRepository reasons,
            RecommendationHistoryRepository histories, DiagnosisRepository diagnoses, GoalRepository goals,
            QueryPeriodPolicy periodPolicy, RecommendationClock clock, RecommendationSimulationPresetAssembler presets) {
        this.recommendations = recommendations;
        this.reasons = reasons;
        this.histories = histories;
        this.diagnoses = diagnoses;
        this.goals = goals;
        this.periodPolicy = periodPolicy;
        this.clock = clock;
        this.presets = presets;
    }

    public PageResponse<RecommendationResponse> list(Long userId, RecommendationStatus status, String domain, int page, int size) {
        Page<Recommendation> found = recommendations.findOwned(userId, status, domain, PageRequest.of(page, size));
        return PageResponse.from(found, withPresets(userId, found.getContent()));
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
        List<Recommendation> open = recommendations
                .findAllByUserIdAndDiagnosisIdOrderByCreatedAtDescRecommendationIdDesc(userId, diagnosisId).stream()
                .filter(r -> OPEN_STATUSES.contains(r.getStatus()))
                .sorted(Comparator.comparingInt(Recommendation::getPriority).thenComparing(Recommendation::getRecommendationId))
                .toList();
        List<RecommendationResponse> items = open.stream().map(withPresets(userId, open)).toList();
        return new RecommendationPrioritiesResponse(diagnosisId, goalId, items);
    }

    public RecommendationResponse detail(Long userId, Long recommendationId) {
        Recommendation recommendation = owned(userId, recommendationId);
        return RecommendationResponse.from(recommendation, presets.assemble(userId, recommendation));
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

    /** 사용자의 모든 추천에 걸친 상태 이력. 기간은 공통 정책(기본 3개월·최대 12개월)이며 {@code [start, end + 1일)}로 조회한다. */
    public PageResponse<RecommendationHistoryResponse> history(Long userId, LocalDate startDate, LocalDate endDate, int page, int size) {
        QueryPeriod period = periodPolicy.resolve(startDate, endDate, clock.now().toLocalDate());
        Page<RecommendationHistory> found = histories.findOwnedInPeriod(userId, period.startDate().atStartOfDay(),
                period.endDate().plusDays(1).atStartOfDay(), PageRequest.of(page, size));
        List<Long> ids = found.getContent().stream().map(RecommendationHistory::getRecommendationId).distinct().toList();
        Map<Long, Recommendation> headers = ids.isEmpty() ? Map.of()
                : recommendations.findAllByUserIdAndRecommendationIdIn(userId, ids).stream()
                        .collect(Collectors.toMap(Recommendation::getRecommendationId, Function.identity()));
        return PageResponse.from(found, h -> RecommendationHistoryResponse.of(h, headers.get(h.getRecommendationId())));
    }

    /** 목록·priorities의 프리셋은 항목별로 조회하지 않고 한 번에 조립한다. */
    private Function<Recommendation, RecommendationResponse> withPresets(Long userId, List<Recommendation> all) {
        Map<Long, RecommendationSimulationPreset> byId = presets.assembleAll(userId, all);
        return r -> RecommendationResponse.from(r, byId.get(r.getRecommendationId()));
    }

    Recommendation owned(Long userId, Long recommendationId) {
        return recommendations.findByRecommendationIdAndUserId(recommendationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }
}
