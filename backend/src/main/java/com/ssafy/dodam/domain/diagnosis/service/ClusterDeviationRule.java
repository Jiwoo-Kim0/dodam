package com.ssafy.dodam.domain.diagnosis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.diagnosis.entity.DetectionSource;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.pattern.dto.response.ClusterProfileResponse;
import com.ssafy.dodam.domain.pattern.dto.response.ClusterProfileResponse.FeatureRange;
import com.ssafy.dodam.domain.pattern.dto.response.PatternFeatureSummary;
import com.ssafy.dodam.domain.pattern.entity.PatternAnalysis;
import com.ssafy.dodam.domain.pattern.entity.PatternAnalysisStatus;
import com.ssafy.dodam.domain.pattern.repository.JdbcPatternFeatureReader;
import com.ssafy.dodam.domain.pattern.repository.PatternAnalysisRepository;
import com.ssafy.dodam.domain.pattern.service.PatternProperties;
import com.ssafy.dodam.domain.pattern.service.PatternResponseAssembler;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 군집 이탈 진단 규칙(S15P21D105-641, 기획안 5-2 "군집 범위 이탈"·14-3 Hybrid). K-Means 배정 결과에서 특징이 배정 군집의
 * 기준 범위(5~95분위)를 벗어난 것을 진단 문제로 만들거나, 같은 성격의 규칙 문제가 있으면 그 문제에 군집 근거만 붙인다.
 *
 * <p><b>새 계산은 없다.</b> 판정에 쓰는 값은 전부 패턴 분석이 완료 시점에 {@code pattern_analysis.cluster_stats_json}에
 * 굳혀 둔 것이다({@link ClusterProfileResponse}: 특징별 {@code deviation}·{@code direction}·{@code p5}·{@code p95}). 여기서는
 * 임계값과 방향 필터만 적용하므로 모델을 다시 학습해도 과거 진단은 바뀌지 않고, 군집 판정 근거(모델 버전·표본 수·기준 기간)가
 * 문제에 그대로 남는다.
 *
 * <p>판정 순서:
 * <ol>
 *   <li>최신 COMPLETED 분석이 없으면 {@code PATTERN_NOT_ANALYZED}. 군집이 없으면(RULE_ONLY 전환) 분석이 남긴 {@code fallbackReason}.</li>
 *   <li>분석 시각이 기준일에서 {@link #STALE_DAYS}일보다 오래됐으면 {@code STALE_PATTERN_ANALYSIS} — 낡은 배정으로 판정하지 않는다.</li>
 *   <li>노출 조건(분석 단계에서 이미 검사한 값을 진단에서 다시 확인): 표본 ≥ {@code min-cluster-sample-size}, 신뢰도 ≥ {@code min-confidence},
 *       데이터 개월 ≥ {@code min-user-months}.</li>
 *   <li>{@link ClusterDeviationFeature} 표의 "점검 방향"과 일치하고 {@code deviation ≥} {@link #FEATURE_DEVIATION_THRESHOLD}인 특징마다 후보.
 *       저축률이 군집보다 높은 것은 문제가 아니다.</li>
 * </ol>
 * 심각도는 {@code deviation ≥} {@link #HIGH_SEVERITY_DEVIATION}(기준 범위 폭만큼 벗어남)이면 HIGH, 그 외 MEDIUM.
 * <b>임계값·경계·30일은 모두 서비스 초기값</b>이며 시연 seed 실측 뒤 조정한다(627과 같은 처리).
 *
 * <p>병합: 같은 진단 안에 표의 "병합 대상" 규칙 문제가 있으면 새 문제를 만들지 않고 그 문제의 {@code detectionSource}를
 * {@code RULE_AND_CLUSTER}로 바꾸고 {@code clusterContext}를 채운다(기획안 5-2 "근거만 병합"). 없으면 {@code CLUSTER} 단독 문제를 만든다.
 * 단독 문제의 제목은 단정하지 않는다 — "낭비"가 아니라 "비슷한 소비 유형과 견줘 점검할 항목".
 */
@Component
@Transactional(readOnly = true)
public class ClusterDeviationRule {
    public static final String COVERAGE_KEY = "CLUSTER";
    /** 이 값 이상 기준 범위를 벗어난 특징만 문제로 본다(범위 폭 대비 비율). 서비스 초기값. */
    public static final double FEATURE_DEVIATION_THRESHOLD = 0.25;
    /** 기준 범위 폭만큼(1.0) 벗어나면 HIGH. 서비스 초기값. */
    public static final double HIGH_SEVERITY_DEVIATION = 1.0;
    /** 분석이 이보다 오래됐으면 판정하지 않는다. 서비스 초기값. */
    public static final int STALE_DAYS = 30;

    public static final String REASON_NOT_ANALYZED = "PATTERN_NOT_ANALYZED";
    public static final String REASON_CLUSTER_NOT_ASSIGNED = "CLUSTER_NOT_ASSIGNED";
    public static final String REASON_STALE = "STALE_PATTERN_ANALYSIS";
    public static final String REASON_SMALL_SAMPLE = "SMALL_CLUSTER_SAMPLE";
    public static final String REASON_LOW_CONFIDENCE = "LOW_CONFIDENCE";
    public static final String REASON_USER_DATA_TOO_SHORT = "USER_DATA_TOO_SHORT";
    public static final String REASON_STATS_UNREADABLE = "CLUSTER_STATS_UNREADABLE";

    static final String TITLE = "비슷한 소비 유형과 견줘 점검할 항목이 있어요";

    private final PatternAnalysisRepository analyses;
    private final PatternResponseAssembler assembler;
    private final PatternProperties properties;
    private final ObjectMapper json;

    public ClusterDeviationRule(PatternAnalysisRepository analyses, PatternResponseAssembler assembler,
            PatternProperties properties, ObjectMapper json) {
        this.analyses = analyses;
        this.assembler = assembler;
        this.properties = properties;
        this.json = json;
    }

    public Result evaluate(Long userId, LocalDate asOf) {
        Optional<PatternAnalysis> latest = analyses.findFirstByUserIdAndStatusOrderByAnalyzedAtDescIdDesc(userId,
                PatternAnalysisStatus.COMPLETED);
        if (latest.isEmpty()) {
            return Result.unavailable(REASON_NOT_ANALYZED, null);
        }
        PatternAnalysis analysis = latest.get();
        Optional<PatternFeatureSummary> summary = assembler.featureSummary(analysis);
        if (!analysis.hasCluster()) {
            String reason = summary.map(PatternFeatureSummary::fallbackReason).filter(r -> r != null && !r.isBlank())
                    .orElse(REASON_CLUSTER_NOT_ASSIGNED);
            return Result.unavailable(reason, analysis.getId());
        }
        Optional<ClusterProfileResponse> profile = assembler.clusterProfile(analysis);
        if (profile.isEmpty()) {
            return Result.unavailable(REASON_STATS_UNREADABLE, analysis.getId());
        }
        int monthsWithData = summary.map(PatternFeatureSummary::monthsWithData).orElse(0);
        return judge(analysis.getId(), profile.get(), monthsWithData, analysis.getAnalyzedAt(), asOf, properties);
    }

    /**
     * 순수 판정. 저장된 프로필과 분석 메타데이터만 받으므로 테스트가 DB 없이 경계를 확인한다.
     *
     * @param monthsWithData 분석 기간 중 거래가 있던 달 수({@code featureSummary.monthsWithData})
     */
    static Result judge(Long analysisId, ClusterProfileResponse profile, int monthsWithData, LocalDateTime analyzedAt,
            LocalDate asOf, PatternProperties properties) {
        if (analyzedAt == null || analyzedAt.toLocalDate().isBefore(asOf.minusDays(STALE_DAYS))) {
            return Result.unavailable(REASON_STALE, analysisId);
        }
        if (profile.sampleSize() < properties.minClusterSampleSize()) {
            return Result.unavailable(REASON_SMALL_SAMPLE, analysisId);
        }
        if (profile.confidence() < properties.minConfidence()) {
            return Result.unavailable(REASON_LOW_CONFIDENCE, analysisId);
        }
        if (monthsWithData < properties.minUserMonths()) {
            return Result.unavailable(REASON_USER_DATA_TOO_SHORT, analysisId);
        }
        List<Item> items = new ArrayList<>();
        for (FeatureRange range : profile.features()) {
            ClusterDeviationFeature feature = ClusterDeviationFeature.of(range.feature());
            if (feature == null || range.direction() == null || !feature.checkedDirection().equals(range.direction())
                    || range.deviation() < FEATURE_DEVIATION_THRESHOLD) {
                continue;
            }
            IssueSeverity severity = range.deviation() >= HIGH_SEVERITY_DEVIATION ? IssueSeverity.HIGH : IssueSeverity.MEDIUM;
            items.add(new Item(feature, range, severity));
        }
        String window = profile.windowStart() + "~" + profile.windowEnd();
        return new Result(true, "EVALUATED", null, analysisId, profile.modelVersion(), profile.clusterId(), profile.clusterLabel(),
                profile.sampleSize(), profile.confidence(), profile.deviationScore(), window, analyzedAt,
                FEATURE_DEVIATION_THRESHOLD, HIGH_SEVERITY_DEVIATION, List.copyOf(items));
    }

    /**
     * 후보를 규칙 문제에 병합할지, 단독 문제로 만들지 정해 {@code issues}에 반영한다. 병합 대상이 여럿이면(재량 카테고리 여러 개가
     * 늘어난 경우) 전부에 같은 군집 근거를 붙인다 — 어느 하나만 고를 근거가 없고 문제 수는 변하지 않는다.
     *
     * @return 이 호출로 새로 만든 단독 문제 수
     */
    public int apply(Diagnosis diagnosis, Result result, List<DiagnosisIssue> issues) {
        int created = 0;
        for (Item item : result.items()) {
            JsonNode context = clusterContext(result, item);
            List<DiagnosisIssue> targets = issues.stream()
                    .filter(issue -> issue.getDetectionSource() == DetectionSource.RULE)
                    .filter(issue -> item.feature().mergesInto(issue))
                    .toList();
            if (targets.isEmpty()) {
                issues.add(toIssue(diagnosis, result, item, context));
                created++;
            } else {
                targets.forEach(issue -> issue.attachClusterEvidence(result.modelVersion(), context));
            }
        }
        return created;
    }

    DiagnosisIssue toIssue(Diagnosis diagnosis, Result result, Item item, JsonNode context) {
        FeatureRange range = item.range();
        ObjectNode current = json.createObjectNode();
        current.put("feature", range.feature()).put("displayName", range.displayName()).put("userValue", range.userValue())
                .put("direction", range.direction()).put("deviation", range.deviation());
        ObjectNode benchmark = json.createObjectNode();
        benchmark.put("p5", range.p5()).put("p95", range.p95()).put("mean", range.mean()).put("sampleSize", result.sampleSize())
                .put("deviationThreshold", FEATURE_DEVIATION_THRESHOLD).put("highSeverityDeviation", HIGH_SEVERITY_DEVIATION);
        ObjectNode evidence = json.createObjectNode();
        evidence.put("ruleKey", "CLUSTER_" + range.feature()).put("targetId", result.analysisId()).put("patternAnalysisId", result.analysisId())
                .put("clusterId", result.clusterId()).put("clusterLabel", result.clusterLabel());
        return DiagnosisIssue.ofCluster(diagnosis.getDiagnosisId(), item.feature().domain(), item.feature().type(), item.severity(),
                TITLE, description(result, range), current, benchmark, evidence, DetectionSource.CLUSTER, result.modelVersion(),
                context, diagnosis.getCreatedAt());
    }

    /** 코치({@code CoachFactAssembler} "군집 근거(서버 계산)" 섹션)와 FE 상세 카드가 그대로 읽는 JSON. 숫자는 저장값 그대로다. */
    JsonNode clusterContext(Result result, Item item) {
        FeatureRange range = item.range();
        ObjectNode node = json.createObjectNode();
        node.put("modelVersion", result.modelVersion()).put("clusterId", result.clusterId()).put("clusterLabel", result.clusterLabel())
                .put("sampleSize", result.sampleSize()).put("confidence", result.confidence()).put("window", result.window())
                .put("analyzedAt", result.analyzedAt().toString())
                .put("feature", range.feature()).put("displayName", range.displayName())
                .put("userValue", range.userValue()).put("p5", range.p5()).put("p95", range.p95()).put("mean", range.mean())
                .put("deviation", range.deviation()).put("direction", range.direction())
                .put("deviationScore", result.deviationScore());
        if (range.percentileInCluster() != null) {
            node.put("percentileInCluster", range.percentileInCluster());
        }
        if (range.rankBand() != null) {
            node.put("rankBand", range.rankBand());
        }
        return node;
    }

    /** "재량소비 비중이 44%로 비슷한 소비 유형(소비 집중형, 1,000명)의 기준 범위 12~31%보다 높아요." 비율 특징은 %로, 변동성은 소수로. */
    static String description(Result result, FeatureRange range) {
        ClusterDeviationFeature feature = ClusterDeviationFeature.of(range.feature());
        boolean percent = feature == null || feature.percentDisplay();
        String comparison = "ABOVE".equals(range.direction()) ? "보다 높아요." : "보다 낮아요.";
        return range.displayName() + "이 " + number(range.userValue(), percent) + unit(percent) + "로 비슷한 소비 유형(" + result.clusterLabel()
                + ", " + String.format("%,d", result.sampleSize()) + "명)의 기준 범위 " + number(range.p5(), percent) + "~"
                + number(range.p95(), percent) + unit(percent) + comparison;
    }

    private static String number(double value, boolean percent) {
        if (percent) {
            return BigDecimal.valueOf(value * 100).setScale(0, RoundingMode.HALF_UP).toPlainString();
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String unit(boolean percent) {
        return percent ? "%" : "";
    }

    /**
     * 특징별 점검 방향·문제 유형·병합 대상(기획안 5-2 표). {@code spendingToIncomeRatio}·{@code savingRate}는 성격상 GOAL 위험과 겹치지만
     * FE가 GOAL 도메인을 발견 목록에서 숨기므로(607) 병합하면 배지가 화면에 나오지 않아 단독 생성한다.
     */
    public enum ClusterDeviationFeature {
        SPENDING_TO_INCOME_RATIO("spendingToIncomeRatio", "ABOVE", "SPENDING", "CLUSTER_SPENDING_RATIO_HIGH", null, null, true),
        SAVING_RATE("savingRate", "BELOW", "SAVING", "CLUSTER_SAVING_RATE_LOW", null, null, true),
        DISCRETIONARY_SHARE("discretionaryShare", "ABOVE", "SPENDING", "CLUSTER_DISCRETIONARY_HIGH",
                "SPENDING", "CATEGORY_SPENDING_INCREASE", true),
        FIXED_EXPENSE_SHARE("fixedExpenseShare", "ABOVE", "SPENDING", "CLUSTER_FIXED_SHARE_HIGH", null, null, true),
        SPENDING_VOLATILITY("spendingVolatility", "ABOVE", "CASHFLOW", "CLUSTER_VOLATILITY_HIGH", "CASHFLOW", "BALANCE_SHORTAGE", false);

        private final String key;
        private final String checkedDirection;
        private final String domain;
        private final String type;
        private final String mergeDomain;
        private final String mergeType;
        private final boolean percentDisplay;

        ClusterDeviationFeature(String key, String checkedDirection, String domain, String type, String mergeDomain, String mergeType,
                boolean percentDisplay) {
            this.key = key;
            this.checkedDirection = checkedDirection;
            this.domain = domain;
            this.type = type;
            this.mergeDomain = mergeDomain;
            this.mergeType = mergeType;
            this.percentDisplay = percentDisplay;
        }

        public static ClusterDeviationFeature of(String key) {
            for (ClusterDeviationFeature feature : values()) {
                if (feature.key.equals(key)) {
                    return feature;
                }
            }
            return null;
        }

        public String key() {
            return key;
        }

        public String checkedDirection() {
            return checkedDirection;
        }

        public String domain() {
            return domain;
        }

        public String type() {
            return type;
        }

        boolean percentDisplay() {
            return percentDisplay;
        }

        /**
         * 이 규칙 문제에 근거를 병합할 수 있는지. 재량 소비 비중은 재량 카테고리(FOOD·CAFE·SHOPPING·BEAUTY·CULTURE·TRAVEL·TAXI)의
         * 소비 증가 문제에만 붙는다 — 마트·교통 증가는 재량 소비 이탈의 근거가 아니다.
         */
        public boolean mergesInto(DiagnosisIssue issue) {
            if (mergeDomain == null || !mergeDomain.equals(issue.getDomain()) || !mergeType.equals(issue.getType())) {
                return false;
            }
            if (this != DISCRETIONARY_SHARE) {
                return true;
            }
            JsonNode evidence = issue.getEvidence();
            String category = evidence == null ? null : evidence.path("categoryCode").asText(null);
            return category != null && Set.copyOf(JdbcPatternFeatureReader.DISCRETIONARY_CATEGORY_CODES).contains(category);
        }
    }

    /**
     * @param available        판정을 수행했는지. false면 {@code coverageReason}에 사유
     * @param coverageStatus   EVALUATED / UNAVAILABLE
     * @param analysisId       근거 패턴 분석. 분석이 없으면 null
     * @param deviationScore   특징 5종 이탈의 균등 평균(프로필 값). 개별 특징 deviation보다 클 수 없다
     * @param window           특징 산출 기간 "yyyy-MM~yyyy-MM"
     * @param items            임계값을 넘은 특징. 비어 있으면 모두 기준 범위 안
     */
    public record Result(boolean available, String coverageStatus, String coverageReason, Long analysisId, String modelVersion,
            Integer clusterId, String clusterLabel, Integer sampleSize, Double confidence, Double deviationScore, String window,
            LocalDateTime analyzedAt, double deviationThreshold, double highSeverityDeviation, List<Item> items) {

        static Result unavailable(String reason, Long analysisId) {
            return new Result(false, "UNAVAILABLE", reason, analysisId, null, null, null, null, null, null, null, null,
                    FEATURE_DEVIATION_THRESHOLD, HIGH_SEVERITY_DEVIATION, List.of());
        }
    }

    public record Item(ClusterDeviationFeature feature, FeatureRange range, IssueSeverity severity) {
    }
}
