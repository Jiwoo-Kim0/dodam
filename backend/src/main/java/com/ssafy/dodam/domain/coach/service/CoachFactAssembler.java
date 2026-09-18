package com.ssafy.dodam.domain.coach.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.coach.dto.CoachFacts;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Fact;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Section;
import com.ssafy.dodam.domain.coach.dto.CoachTopic;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisIssueRepository;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import com.ssafy.dodam.domain.pattern.dto.response.ClusterProfileResponse;
import com.ssafy.dodam.domain.pattern.dto.response.PatternInsight;
import com.ssafy.dodam.domain.pattern.dto.response.PeerComparisonResponse;
import com.ssafy.dodam.domain.pattern.service.PeerComparisonCalculator;
import com.ssafy.dodam.domain.pattern.service.PeerDimension;
import com.ssafy.dodam.domain.user.entity.Gender;
import com.ssafy.dodam.domain.pattern.entity.PatternAnalysis;
import com.ssafy.dodam.domain.pattern.entity.PatternAnalysisStatus;
import com.ssafy.dodam.domain.pattern.repository.PatternAnalysisRepository;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationReason;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationReasonRepository;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationRepository;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프롬프트 입력을 만든다. 명세 21의 "서버 계산 결과만 입력으로 사용"이 지켜지는 곳이자, 소유권 검증이
 * 이루어지는 곳이다.
 *
 * <p><b>소유권</b>: 모든 조회는 {@code userId} 조건이 <b>쿼리 안에</b> 걸린 메서드만 쓴다
 * ({@code DiagnosisIssueRepository}·{@code RecommendationReasonRepository}는 하위 테이블에 `user_id`가
 * 없어 상위 테이블을 조인한다). 남의 리소스와 없는 리소스는 모두 {@link ResourceNotFoundException}으로
 * 같게 응답한다 — 존재 여부를 구분해 주면 ID를 훑어 남의 진단 개수를 셀 수 있다.
 *
 * <p><b>재계산 없음</b>: 여기서 새로 계산하는 값은 하나도 없다. 저장된 집계값을 읽어 세는 것
 * (영역별·심각도별 문제 수)까지가 전부이며, 그마저 {@code DiagnosisQueryService.summary}가 이미 하는
 * 방식 그대로다. 진단 규칙·추천 효과·군집 기준 범위는 각 도메인이 계산해 저장한 값을 옮기기만 한다.
 *
 * <p><b>군집</b>: 명세는 "군집 라벨·기준 범위는 서버 계산값만 전달", "군집 판정 금지"를 요구한다.
 * {@code pattern_analysis.cluster_stats_json}에 저장된 {@link ClusterProfileResponse}가 이미 그 형태라
 * 라벨·표본 수·신뢰도·특징별 기준 범위를 그대로 옮긴다. 저장된 군집 결과가 없으면 군집 구간을 통째로
 * 빼고, 모델이 또래 비교를 지어내지 않도록 {@code notes}에 명시한다.
 */
@Service
@Transactional(readOnly = true)
public class CoachFactAssembler {

    static final String DIAGNOSIS_NOT_FOUND = "완료된 진단을 찾을 수 없습니다.";
    static final String ISSUE_NOT_FOUND = "진단 문제를 찾을 수 없습니다.";
    static final String RECOMMENDATION_NOT_FOUND = "개선안을 찾을 수 없습니다.";
    /** 요약에 싣는 주요 문제 수. 전부 넣으면 프롬프트가 길어지고 모델이 나열만 한다. */
    static final int TOP_ISSUE_LIMIT = 3;
    /** 군집 특징은 이탈이 큰 것부터 이만큼만. 12종을 모두 넣으면 무엇이 중요한지 사라진다. */
    static final int TOP_CLUSTER_FEATURE_LIMIT = 3;
    /** 검사하지 못한 규칙 이름은 이만큼만 나열한다. 개수는 따로 싣는다. */
    static final int COVERAGE_NAME_LIMIT = 6;

    private static final Logger log = LoggerFactory.getLogger(CoachFactAssembler.class);
    private static final Comparator<DiagnosisIssue> BY_SEVERITY = Comparator
            .comparingInt((DiagnosisIssue issue) -> -issue.getSeverity().ordinal())
            .thenComparing(DiagnosisIssue::getIssueId);

    private final DiagnosisRepository diagnoses;
    private final DiagnosisIssueRepository issues;
    private final RecommendationRepository recommendations;
    private final RecommendationReasonRepository reasons;
    private final PatternAnalysisRepository patterns;
    private final CoachEvidenceReader evidence;
    private final ObjectMapper json;

    public CoachFactAssembler(DiagnosisRepository diagnoses, DiagnosisIssueRepository issues,
            RecommendationRepository recommendations, RecommendationReasonRepository reasons,
            PatternAnalysisRepository patterns, CoachEvidenceReader evidence, ObjectMapper json) {
        this.diagnoses = diagnoses;
        this.issues = issues;
        this.recommendations = recommendations;
        this.reasons = reasons;
        this.patterns = patterns;
        this.evidence = evidence;
        this.json = json;
    }

    /** 금융현황 AI 요약. {@code diagnosisId}가 없으면 최신 완료 진단을 쓴다. */
    public CoachFacts financialSummary(Long userId, Long diagnosisId) {
        Diagnosis diagnosis = diagnosisId == null ? latestDiagnosis(userId) : ownedDiagnosis(userId, diagnosisId);
        List<DiagnosisIssue> found = issues.findAllOwned(userId, diagnosis.getDiagnosisId());

        List<Section> sections = new ArrayList<>();
        sections.add(new Section("진단 개요", List.of(
                new Fact("진단 완료 시각", CoachFormat.dateTime(diagnosis.getCompletedAt())),
                new Fact("진단 규칙 버전", text(diagnosis.getRuleVersion())),
                new Fact("종합 상태(서버 판정)", text(diagnosis.getOverallStatus())),
                new Fact("탐지된 문제 수", CoachFormat.count(diagnosis.getIssueCount())),
                new Fact("높은 심각도 문제 수", CoachFormat.count(diagnosis.getHighSeverityCount())),
                new Fact("연계된 목표", diagnosis.getGoalId() == null ? "없음" : "있음"))));
        sections.add(new Section("심각도별 문제 수", severityCounts(found)));
        if (!found.isEmpty()) {
            sections.add(new Section("영역별 문제 수", domainCounts(found)));
            sections.add(new Section("주요 문제(심각도 순)", topIssues(found)));
        }

        Coverage coverage = coverage(diagnosis);
        coverage.section().ifPresent(sections::add);

        List<String> notes = new ArrayList<>();
        notes.add("종합 상태·심각도·문제 수는 서버 진단 규칙(" + text(diagnosis.getRuleVersion())
                + ")이 판정해 저장한 값이다. 다시 판정하거나 다른 상태를 제시하지 말 것.");
        if (found.isEmpty()) {
            notes.add("이번 진단에서 탐지된 문제가 없다. 문제를 지어내지 말 것.");
        }
        if (coverage.hasUnavailable()) {
            // 이것을 넣지 않으면 모델은 "문제 0건"을 "이상 없음"으로 옮긴다. 실제로는 검사하지 못한
            // 항목이 있다는 뜻일 수 있고, 그 차이를 사용자가 알아야 한다.
            notes.add("문제 수는 '검사한 범위 안에서'의 수치다. 검사하지 못한 항목이 "
                    + coverage.unavailableCount() + "개 있으므로, 문제가 전혀 없다거나 금융 상태가 건강하다고"
                    + " 단정하지 말고 검사하지 못한 항목이 있다는 점을 반드시 함께 알려라.");
        }
        if (diagnosis.getOverallStatus() == null) {
            notes.add("종합 상태가 저장되지 않은 것은 서버가 판정을 보류했다는 뜻이다."
                    + " 이를 '양호'나 '건강'으로 바꿔 말하지 말 것.");
        }
        clusterSection(userId).ifPresentOrElse(sections::add,
                () -> notes.add("저장된 소비패턴 군집 결과가 없다. 또래·평균과의 비교를 언급하지 말 것."));
        peerSection(userId).ifPresent(sections::add);

        return new CoachFacts(CoachTopic.FINANCIAL_SUMMARY,
                "금융 진단 결과 요약(" + CoachFormat.dateTime(diagnosis.getCompletedAt()) + " 기준)", sections, notes);
    }

    /** 비효율 원인 설명. 진단·문제 모두 본인 것이어야 한다. */
    public CoachFacts inefficiency(Long userId, Long diagnosisId, Long issueId) {
        Diagnosis diagnosis = ownedDiagnosis(userId, diagnosisId);
        DiagnosisIssue issue = issues.findOwnedById(userId, diagnosisId, issueId)
                .orElseThrow(() -> new ResourceNotFoundException(ISSUE_NOT_FOUND));

        List<Fact> overview = new ArrayList<>();
        overview.add(new Fact("영역", issue.getDomain()));
        overview.add(new Fact("유형", issue.getType()));
        overview.add(new Fact("심각도(서버 판정)", issue.getSeverity().name()));
        overview.add(new Fact("탐지 방식", issue.getDetectionSource().name()));
        overview.add(new Fact("탐지 시각", CoachFormat.dateTime(issue.getDetectedAt())));
        if (issue.getModelVersion() != null) {
            overview.add(new Fact("군집 모델 버전", issue.getModelVersion()));
        }
        overview.add(new Fact("진단 규칙 버전", text(diagnosis.getRuleVersion())));

        List<Section> sections = new ArrayList<>();
        sections.add(new Section("문제 개요", overview));
        addIfPresent(sections, "현재 값(서버 계산)", evidence.read(null, issue.getCurrentValue()));
        addIfPresent(sections, "기준 값(서버 계산)", evidence.read(null, issue.getBenchmarkValue()));
        addIfPresent(sections, "탐지 근거(서버 계산)", evidence.read(null, issue.getEvidence()));
        addIfPresent(sections, "군집 근거(서버 계산)", evidence.read(null, issue.getClusterContext()));

        List<String> notes = new ArrayList<>();
        notes.add("서버가 저장한 문제 설명: " + issue.getDescription());
        notes.add("심각도 " + issue.getSeverity().name() + "은(는) 서버 진단 규칙이 판정한 값이다."
                + " 심각도를 다시 매기거나 문제 여부를 새로 판단하지 말 것.");
        if (issue.getClusterContext() == null) {
            notes.add("이 문제에는 군집 근거가 없다. 또래 집단 비교를 만들어 내지 말 것.");
        } else {
            // 640: 군집 이탈은 낭비 판정이 아니다(기획안 5-7). 문장은 "비슷한 소비 유형 사용자군 대비 점검이 필요한 항목"에 머문다.
            notes.add("군집 근거는 비슷한 소비 유형 사용자군과의 비교일 뿐이다. 낭비·잘못으로 단정하지 말고"
                    + " '비슷한 소비 유형 사용자군 대비 점검이 필요한 항목'으로 표현할 것. 표본 수·기준 기간은 근거에 있는 값만 쓸 것.");
        }

        return new CoachFacts(CoachTopic.INEFFICIENCY_EXPLANATION, issue.getTitle(), sections, notes);
    }

    /** 추천 근거 설명. 효과 금액·우선순위는 저장값 그대로다. */
    public CoachFacts recommendation(Long userId, Long recommendationId) {
        Recommendation target = recommendations.findByRecommendationIdAndUserId(recommendationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(RECOMMENDATION_NOT_FOUND));

        List<Section> sections = new ArrayList<>();
        sections.add(new Section("개선안 개요", List.of(
                new Fact("영역", target.getDomain()),
                new Fact("유형", target.getType()),
                new Fact("우선순위(서버 계산)", String.valueOf(target.getPriority())),
                new Fact("상태", target.getStatus().name()),
                new Fact("생성 시각", CoachFormat.dateTime(target.getCreatedAt())))));

        List<Fact> effect = effectFacts(target);
        if (!effect.isEmpty()) {
            sections.add(new Section("예상 효과(서버 계산)", effect));
        }
        List<RecommendationReason> stored = reasons.findOwned(userId, recommendationId);
        if (!stored.isEmpty()) {
            sections.add(new Section("서버가 저장한 근거", reasonFacts(stored)));
        }

        List<String> notes = new ArrayList<>();
        notes.add("서버가 저장한 개선안 설명: " + target.getDescription());
        notes.add("우선순위와 효과 금액은 서버 추천 엔진이 계산해 저장한 값이다."
                + " 금액을 더하거나 연·월로 환산하는 등 새로 계산하지 말 것.");
        if (effect.isEmpty()) {
            notes.add("이 개선안은 효과 금액이 계산되지 않았다. 절감액을 추정하거나 제시하지 말 것.");
        }
        if (stored.isEmpty()) {
            notes.add("저장된 근거가 없다. 위 개요와 설명 범위를 넘는 근거를 만들어 내지 말 것.");
        }

        return new CoachFacts(CoachTopic.RECOMMENDATION_EXPLANATION, target.getTitle(), sections, notes);
    }

    /**
     * 진단이 어디까지 검사했는지. 진단 스냅샷의 `coverage`를 그대로 옮긴다(서버 계산값).
     *
     * <p><b>왜 필요한가</b>: `issueCount = 0`은 "다 검사했는데 이상 없음"과 "검사를 못 함"을 구분하지
     * 않는다. 현재 진단 규칙 12개 중 10개가 미구현·미연동으로 항상 `UNAVAILABLE`이고, 나머지 2개도
     * 자료가 없으면 건너뛴다. 이 구간이 없으면 모델은 "문제 0건"을 "금융 상태가 건강하다"로 옮기게
     * 되고, 그것은 서버가 하지 않은 판정이다(명세 21: AI 자체 판정 금지).
     */
    private Coverage coverage(Diagnosis diagnosis) {
        JsonNode snapshot = diagnosis.getInputSnapshot();
        JsonNode entries = snapshot == null ? null : snapshot.get("coverage");
        if (entries == null || !entries.isArray() || entries.isEmpty()) {
            return new Coverage(List.of(), List.of());
        }
        List<String> evaluated = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        entries.forEach(entry -> {
            String ruleKey = entry.path("ruleKey").asText(null);
            if (ruleKey == null) {
                return;
            }
            // NOT_APPLICABLE은 "해당 없음"이라 검사 누락이 아니다(예: 목표가 없는 사용자).
            if ("EVALUATED".equals(entry.path("status").asText())) {
                evaluated.add(ruleKey);
            } else if ("UNAVAILABLE".equals(entry.path("status").asText())) {
                unavailable.add(ruleKey);
            }
        });
        return new Coverage(evaluated, unavailable);
    }

    /** @param evaluated 실제로 검사한 규칙 @param unavailable 자료·구현이 없어 검사하지 못한 규칙 */
    private record Coverage(List<String> evaluated, List<String> unavailable) {

        boolean hasUnavailable() {
            return !unavailable.isEmpty();
        }

        int unavailableCount() {
            return unavailable.size();
        }

        Optional<Section> section() {
            if (evaluated.isEmpty() && unavailable.isEmpty()) {
                return Optional.empty();
            }
            List<Fact> facts = new ArrayList<>();
            facts.add(new Fact("검사한 항목 수", CoachFormat.count(evaluated.size())));
            facts.add(new Fact("검사하지 못한 항목 수", CoachFormat.count(unavailable.size())));
            if (!evaluated.isEmpty()) {
                facts.add(new Fact("검사한 항목", String.join(", ", evaluated)));
            }
            if (!unavailable.isEmpty()) {
                facts.add(new Fact("검사하지 못한 항목", String.join(", ",
                        unavailable.size() <= COVERAGE_NAME_LIMIT ? unavailable
                                : unavailable.subList(0, COVERAGE_NAME_LIMIT))));
            }
            return Optional.of(new Section("진단 검사 범위(서버 계산)", facts));
        }
    }

    private List<Fact> effectFacts(Recommendation target) {
        List<Fact> facts = new ArrayList<>();
        addIfNotNull(facts, "월 절감액", CoachFormat.money(target.getMonthlyCostSaving()));
        addIfNotNull(facts, "연 절감액", CoachFormat.money(target.getAnnualCostSaving()));
        addIfNotNull(facts, "이자 절감액", CoachFormat.money(target.getInterestSaving()));
        addIfNotNull(facts, "위험 감소", target.getRiskReduction());
        addIfNotNull(facts, "현재 예상 달성일", CoachFormat.date(target.getCurrentForecastDate()));
        addIfNotNull(facts, "개선 후 예상 달성일", CoachFormat.date(target.getImprovedForecastDate()));
        addIfNotNull(facts, "앞당겨지는 기간", CoachFormat.days(target.getAdvancedDays()));
        addIfNotNull(facts, "목표 기여 금액", CoachFormat.money(target.getContributionAmount()));
        return facts;
    }

    private List<Fact> reasonFacts(List<RecommendationReason> stored) {
        List<Fact> facts = new ArrayList<>();
        for (RecommendationReason reason : stored) {
            facts.add(new Fact(reason.getCode(), reason.getDescription()));
            facts.addAll(evidence.read(reason.getCode(), reason.getEvidence()));
        }
        return facts;
    }

    /**
     * 최신 완료 패턴 분석의 군집 결과. 저장된 {@code cluster_stats_json}을 읽기만 하며 군집을 다시 배정하지 않는다.
     *
     * <p>JSON 해석에 실패해도 예외를 올리지 않는다. 2026-09-14 이전 저장분처럼 형식이 다른 행이 있을 수 있고,
     * 군집은 요약의 보조 정보라 그것 때문에 요약 자체를 못 주는 편이 더 나쁘다. 라벨만이라도 살린다.
     */
    private Optional<Section> clusterSection(Long userId) {
        Optional<PatternAnalysis> latest =
                patterns.findFirstByUserIdAndStatusOrderByAnalyzedAtDescIdDesc(userId, PatternAnalysisStatus.COMPLETED);
        if (latest.isEmpty() || latest.get().getClusterLabel() == null) {
            return Optional.empty();
        }
        PatternAnalysis analysis = latest.get();
        List<Fact> facts = new ArrayList<>();
        facts.add(new Fact("군집 라벨(서버 계산)", analysis.getClusterLabel()));
        addIfNotNull(facts, "군집 모델 버전", analysis.getModelVersion());
        if (analysis.getSampleSize() != null) {
            facts.add(new Fact("같은 군집 표본 수", CoachFormat.count(analysis.getSampleSize())));
        }
        addIfNotNull(facts, "분류 신뢰도", CoachFormat.percent(analysis.getConfidence()));
        facts.addAll(clusterFeatureFacts(analysis.getClusterStats()));
        return Optional.of(new Section("소비패턴 군집(서버 계산)", facts));
    }

    /**
     * 632 동년배 비교. 분석 완료 시점에 저장된 {@code peer_stats_json}의 기본 축(나이대×성별, 표본 부족 시 확장된 축)만 옮긴다.
     * 서버가 만든 문장·백분위를 그대로 사실로 주고, 비교할 수 없는 사유가 있으면 그 사실만 준다 — 모델이 또래 비교를 지어내지 않게.
     */
    private Optional<Section> peerSection(Long userId) {
        Optional<PatternAnalysis> latest =
                patterns.findFirstByUserIdAndStatusOrderByAnalyzedAtDescIdDesc(userId, PatternAnalysisStatus.COMPLETED);
        if (latest.isEmpty() || latest.get().getPeerStats() == null || latest.get().getPeerStats().isNull()) {
            return Optional.empty();
        }
        JsonNode node = latest.get().getPeerStats().get(PeerDimension.DEFAULT.name());
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        PeerComparisonResponse peer;
        try {
            peer = json.treeToValue(node, PeerComparisonResponse.class);
        } catch (Exception exception) {
            log.warn("Stored peer comparison is not readable; peer section is skipped");
            return Optional.empty();
        }
        List<Fact> facts = new ArrayList<>();
        if (peer.peerGroup() == null) {
            facts.add(new Fact("또래 비교 불가 사유(서버 판정)", peer.unavailableReason()));
            return Optional.of(new Section("또래 비교(서버 계산)", facts));
        }
        facts.add(new Fact("비교 집단", PeerComparisonCalculator.groupDisplay(PeerDimension.valueOf(peer.peerGroup().dimension()),
                peer.peerGroup().ageBand(), peer.peerGroup().gender() == null ? null : Gender.valueOf(peer.peerGroup().gender()),
                latest.get().getClusterLabel()) + " " + CoachFormat.count(peer.peerGroup().sampleSize()) + "명"));
        if (peer.unavailableReason() != null) {
            facts.add(new Fact("비교 집단 참고", "요청 집단 표본이 부족해 더 넓은 집단으로 비교함"));
        }
        for (PeerComparisonResponse.PeerFeature feature : peer.features()) {
            facts.add(new Fact(feature.displayName() + " 또래 백분위", CoachFormat.number(feature.percentileInPeer())));
        }
        for (PatternInsight insight : peer.insights()) {
            facts.add(new Fact("또래 비교 문장(서버 생성)", insight.message()));
        }
        return Optional.of(new Section("또래 비교(서버 계산)", facts));
    }

    private List<Fact> clusterFeatureFacts(JsonNode clusterStats) {
        if (clusterStats == null || clusterStats.isNull()) {
            return List.of();
        }
        ClusterProfileResponse profile;
        try {
            profile = json.treeToValue(clusterStats, ClusterProfileResponse.class);
        } catch (Exception exception) {
            log.warn("Stored cluster profile is not readable; cluster feature ranges are skipped");
            return List.of();
        }
        return profile.features().stream()
                .sorted(Comparator.comparingDouble(ClusterProfileResponse.FeatureRange::deviation).reversed())
                .limit(TOP_CLUSTER_FEATURE_LIMIT)
                .map(feature -> new Fact(feature.displayName() + " (내 값 / 기준 범위)",
                        CoachFormat.number(feature.userValue()) + " / "
                                + CoachFormat.number(feature.p5()) + "~" + CoachFormat.number(feature.p95())
                                + " (" + feature.direction() + ")"))
                .toList();
    }

    private static List<Fact> severityCounts(List<DiagnosisIssue> found) {
        Map<IssueSeverity, Long> counts = new LinkedHashMap<>();
        for (IssueSeverity severity : IssueSeverity.values()) {
            counts.put(severity, 0L);
        }
        found.forEach(issue -> counts.merge(issue.getSeverity(), 1L, Long::sum));
        return counts.entrySet().stream()
                .map(entry -> new Fact(entry.getKey().name(), CoachFormat.count(entry.getValue())))
                .toList();
    }

    private static List<Fact> domainCounts(List<DiagnosisIssue> found) {
        Map<String, Long> counts = new TreeMap<>();
        found.forEach(issue -> counts.merge(issue.getDomain(), 1L, Long::sum));
        return counts.entrySet().stream()
                .map(entry -> new Fact(entry.getKey(), CoachFormat.count(entry.getValue())))
                .toList();
    }

    private static List<Fact> topIssues(List<DiagnosisIssue> found) {
        return found.stream().sorted(BY_SEVERITY).limit(TOP_ISSUE_LIMIT)
                .map(issue -> new Fact(issue.getSeverity().name() + " / " + issue.getDomain(), issue.getTitle()))
                .toList();
    }

    private Diagnosis latestDiagnosis(Long userId) {
        return diagnoses.findFirstByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDescDiagnosisIdDesc(userId)
                .orElseThrow(() -> new ResourceNotFoundException(DIAGNOSIS_NOT_FOUND));
    }

    private Diagnosis ownedDiagnosis(Long userId, Long diagnosisId) {
        return diagnoses.findByDiagnosisIdAndUserId(diagnosisId, userId)
                .filter(Diagnosis::isCompleted)
                .orElseThrow(() -> new ResourceNotFoundException(DIAGNOSIS_NOT_FOUND));
    }

    private static void addIfPresent(List<Section> sections, String title, List<Fact> facts) {
        if (!facts.isEmpty()) {
            sections.add(new Section(title, facts));
        }
    }

    /**
     * 집계값이 비어 있을 수 있는 자리를 사람이 읽는 문자열로 바꾼다. 그대로 두면 프롬프트에 "null"이라는
     * 단어가 실리고, 모델은 그것을 값으로 읽어 문장에 옮긴다.
     */
    private static String text(Object value) {
        return value == null ? "미기록" : String.valueOf(value);
    }

    private static void addIfNotNull(List<Fact> facts, String label, String value) {
        if (value != null) {
            facts.add(new Fact(label, value));
        }
    }
}
