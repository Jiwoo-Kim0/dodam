package com.ssafy.dodam.domain.recommendation.service;

import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisIssueRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final DiagnosisIssueRepository issues;
    private final GoalRepository goals;
    private final QueryPeriodPolicy periodPolicy;
    private final RecommendationClock clock;
    private final RecommendationSimulationPresetAssembler presets;

    public RecommendationQueryService(RecommendationRepository recommendations, RecommendationReasonRepository reasons,
            RecommendationHistoryRepository histories, DiagnosisRepository diagnoses, DiagnosisIssueRepository issues,
            GoalRepository goals, QueryPeriodPolicy periodPolicy, RecommendationClock clock,
            RecommendationSimulationPresetAssembler presets) {
        this.recommendations = recommendations;
        this.reasons = reasons;
        this.histories = histories;
        this.diagnoses = diagnoses;
        this.issues = issues;
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
        return new RecommendationSummaryResponse(all.size(), byStatus, byDomain, monthly, contribution, discovery(userId, all));
    }

    /**
     * 발견 화면 통합 수치. 사용자 전체 합계는 진단을 거듭할수록 같은 문제의 추천이 쌓여 부풀므로, 최신 완료 진단 한 건의
     * 열린 추천만 보고 같은 문제의 대안은 절감액이 큰 하나만 센다. 목표 단축일은 추천끼리 효과가 겹쳐 더할 수 없고
     * 복합 시뮬레이션은 저장 부수효과가 있어 조회에서 돌리지 않으므로, 최댓값을 주고 그 사실을 note로 남긴다.
     *
     * <p>(675) 목표 위험(GOAL 도메인 문제)은 발견 목록에 올리지 않으므로(607) 문제 수와 그 문제에서 나온 추천(저축 늘리기·소비 줄이기)을
     * 여기서 뺀다. 그렇지 않으면 화면 목록에 없는 항목의 절감액·단축일이 통합 수치에 들어가 목록과 어긋난다. 뺀 사실은
     * {@code GOAL_ISSUES_EXCLUDED}로 남긴다. {@code sourceIssueId}가 없는 추천(628 이전)은 어느 문제인지 알 수 없어 그대로 센다.
     */
    private RecommendationSummaryResponse.Discovery discovery(Long userId, List<Recommendation> all) {
        Optional<Diagnosis> latest = diagnoses.findFirstByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDescDiagnosisIdDesc(userId);
        if (latest.isEmpty()) {
            return null;
        }
        Diagnosis diagnosis = latest.get();
        List<DiagnosisIssue> allIssues = issues.findAllOwned(userId, diagnosis.getDiagnosisId());
        Set<Long> excludedIssueIds = allIssues.stream()
                .filter(i -> RecommendationSummaryResponse.Discovery.EXCLUDED_ISSUE_DOMAIN.equals(i.getDomain()))
                .map(DiagnosisIssue::getIssueId)
                .collect(Collectors.toSet());
        int discoveryIssueCount = allIssues.size() - excludedIssueIds.size();
        List<Recommendation> open = all.stream()
                .filter(r -> diagnosis.getDiagnosisId().equals(r.getDiagnosisId()) && EXPECTED_STATUSES.contains(r.getStatus()))
                .filter(r -> r.getSourceIssueId() == null || !excludedIssueIds.contains(r.getSourceIssueId()))
                .toList();
        List<String> notes = new ArrayList<>();
        if (!excludedIssueIds.isEmpty()) {
            notes.add(RecommendationSummaryResponse.Discovery.GOAL_ISSUES_EXCLUDED);
        }
        if (open.isEmpty()) {
            notes.add(RecommendationSummaryResponse.Discovery.NO_OPEN_RECOMMENDATION);
        }

        // sourceIssueId가 없는 추천(628 이전·여러 문제 합산)은 서로 다른 문제로 보고 각각 센다.
        Map<Object, Long> bestByIssue = new LinkedHashMap<>();
        for (Recommendation r : open) {
            if (r.getAnnualCostSaving() == null) {
                continue;
            }
            Object key = r.getSourceIssueId() == null ? "R" + r.getRecommendationId() : r.getSourceIssueId();
            bestByIssue.merge(key, r.getAnnualCostSaving(), Math::max);
        }
        Long annual = null;
        if (bestByIssue.isEmpty()) {
            notes.add(RecommendationSummaryResponse.Discovery.ANNUAL_SAVING_UNAVAILABLE);
        } else {
            annual = bestByIssue.values().stream().mapToLong(Long::longValue).sum();
            notes.add(RecommendationSummaryResponse.Discovery.ANNUAL_SAVING_BEST_ALTERNATIVE_PER_ISSUE);
        }

        Integer advance = open.stream().map(Recommendation::getAdvancedDays).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        notes.add(advance == null
                ? RecommendationSummaryResponse.Discovery.GOAL_ADVANCE_UNAVAILABLE
                : RecommendationSummaryResponse.Discovery.GOAL_ADVANCE_IS_MAX_NOT_COMBINED);

        return new RecommendationSummaryResponse.Discovery(diagnosis.getDiagnosisId(), discoveryIssueCount,
                open.size(), annual, advance, notes);
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
