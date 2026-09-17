package com.ssafy.dodam.domain.pattern.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.pattern.dto.response.ClusterProfileResponse;
import com.ssafy.dodam.domain.pattern.dto.response.ClusterProfileResponse.FeatureRange;
import com.ssafy.dodam.domain.pattern.dto.response.PatternAnalysisResponse;
import com.ssafy.dodam.domain.pattern.dto.response.PatternAnalysisResponse.Metrics;
import com.ssafy.dodam.domain.pattern.dto.response.PatternFeatureSummary;
import com.ssafy.dodam.domain.pattern.dto.response.PatternInsight;
import com.ssafy.dodam.domain.pattern.entity.AnalysisMode;
import com.ssafy.dodam.domain.pattern.entity.PatternAnalysis;
import com.ssafy.dodam.domain.pattern.service.ClusterAssigner.ClusterResult;
import com.ssafy.dodam.domain.pattern.service.ClusterModel.FeatureStats;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 계산 결과 ↔ 저장 JSON ↔ 응답 변환. 저장 형식이 응답 DTO와 같아 조회 때 재계산 없이 그대로 읽는다.
 */
@Component
public class PatternResponseAssembler {

    private final ObjectMapper objectMapper;

    public PatternResponseAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ---------- 계산 결과 → 저장 형식 ----------

    public PatternFeatureSummary summarize(AnalysisMode requestedMode, PatternFeatures features, ClusterAssigner.Outcome outcome) {
        AnalysisMode effective = outcome.assigned() ? AnalysisMode.HYBRID : AnalysisMode.RULE_ONLY;
        Map<String, Double> clusterFeatures = new LinkedHashMap<>();
        features.clusterFeatureValues().forEach((feature, value) -> clusterFeatures.put(feature.key(), value));
        List<PatternFeatureSummary.Month> months = features.months().stream()
                .map(m -> new PatternFeatureSummary.Month(m.yearMonth().toString(), m.income(), m.spending(), m.fixedSpending(),
                        m.variableSpending(), m.discretionary(), m.savingTransfer(), m.loanRepayment(), m.transactionCount()))
                .toList();
        PatternFeatures.Averages a = features.averages();
        return new PatternFeatureSummary(
                requestedMode.name(), effective.name(),
                outcome.assigned() ? null : outcome.fallbackReason().name(),
                features.window().start().toString(), features.window().end().toString(), features.window().months(),
                features.monthsWithData(), features.incomeBasis().name(), features.monthlyIncome(),
                new PatternFeatureSummary.Averages(a.income(), a.spending(), a.fixedSpending(), a.variableSpending(),
                        a.discretionary(), a.savingTransfer(), a.loanRepayment()),
                features.spendingVolatility(), features.liquidBalance(), features.runwayMonths(), outcome.populationSize(),
                clusterFeatures, months);
    }

    /**
     * 배정된 군집의 프로필. 사용자 값과 군집 기준 범위, 특징별 범위 이탈을 함께 만든다.
     * 632: 모델에 분위 격자가 있으면 특징별 군집 안 백분위·구간, 라벨 대표 특징의 위치 요약, 문장을 더한다. 격자가 없으면(v1) 모두 null.
     */
    public ClusterProfileResponse clusterProfile(ClusterResult cluster) {
        ClusterModel model = cluster.model();
        int clusterId = cluster.summary().clusterId();
        boolean percentiles = model.supportsPercentiles();
        List<FeatureRange> ranges = new ArrayList<>();
        Map<ClusterFeature, Double> percentileByFeature = new EnumMap<>(ClusterFeature.class);
        double deviationSum = 0.0;
        for (ClusterFeature feature : ClusterFeature.ordered()) {
            FeatureStats stats = cluster.summary().stats().get(feature);
            double userValue = cluster.userValues().getOrDefault(feature, 0.0);
            double deviation = Stats.round4(ClusterModel.rangeDeviation(userValue, stats));
            deviationSum += deviation;
            String direction = userValue < stats.p5() ? "BELOW" : userValue > stats.p95() ? "ABOVE" : "WITHIN";
            Double percentile = percentiles ? model.percentileInCluster(clusterId, feature, Stats.round4(userValue)) : null;
            if (percentile != null) {
                percentileByFeature.put(feature, percentile);
            }
            ranges.add(new FeatureRange(feature.key(), feature.displayName(), Stats.round4(userValue), stats.centroid(),
                    stats.min(), stats.max(), stats.p5(), stats.p95(), stats.mean(), stats.std(), deviation, direction,
                    percentile, percentile == null ? null : RankBand.of(percentile).name()));
        }
        ClusterProfileResponse.PositionSummary position = null;
        List<PatternInsight> insights = null;
        if (percentiles) {
            ClusterFeature representative = ClusterLabelPolicy.representativeFeature(cluster.summary().label());
            Double percentile = percentileByFeature.get(representative);
            if (percentile != null) {
                position = new ClusterProfileResponse.PositionSummary(representative.key(), representative.displayName(), percentile,
                        RankBand.of(percentile).name());
            }
            insights = PatternInsightPolicy.clusterInsights(cluster.summary().label(), percentileByFeature);
        }
        return new ClusterProfileResponse(
                clusterId, cluster.summary().label(), model.modelVersion(),
                cluster.summary().sampleSize(), model.populationSize(), cluster.assignment().confidence(),
                model.window().months(), model.window().start().toString(), model.window().end().toString(),
                Stats.round4(deviationSum / ClusterFeature.dimension()), ranges, model.source(), model.trainedBy(),
                position, insights);
    }

    public JsonNode toJson(Object value) {
        return objectMapper.valueToTree(value);
    }

    public JsonNode failureSummary(AnalysisMode requestedMode, PatternWindow window, RuntimeException failure) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("requestedMode", requestedMode.name());
        node.put("windowStart", window.start().toString());
        node.put("windowEnd", window.end().toString());
        node.put("failure", failure.getClass().getSimpleName());
        return node;
    }

    // ---------- 저장 행 → 응답 ----------

    public PatternAnalysisResponse toResponse(PatternAnalysis analysis) {
        PatternFeatureSummary summary = featureSummary(analysis).orElse(null);
        return new PatternAnalysisResponse(
                analysis.getId(), analysis.getStatus().name(), analysis.getAnalyzedAt(), analysis.getLookbackMonths(),
                summary == null ? null : summary.requestedMode(),
                summary == null ? null : summary.analysisMode(),
                summary == null ? null : summary.fallbackReason(),
                new Metrics(
                        toDouble(analysis.getSpendingRegularity()), toDouble(analysis.getSavingConsistency()),
                        toDouble(analysis.getFixedExpenseRatio()), toDouble(analysis.getVariableExpenseRatio()),
                        toDouble(analysis.getDiscretionarySpendingRatio()), toDouble(analysis.getDebtBurden()),
                        toDouble(analysis.getCashflowStability()), analysis.getDataPointCount()),
                analysis.getLiquidityLevel() == null ? null : analysis.getLiquidityLevel().name(),
                summary, clusterProfile(analysis).orElse(null));
    }

    public Optional<PatternFeatureSummary> featureSummary(PatternAnalysis analysis) {
        return read(analysis.getFeatureSummary(), PatternFeatureSummary.class);
    }

    /** 군집 컬럼이 모두 있고 저장 JSON을 읽을 수 있을 때만 프로필을 만든다(명세 "조건 미충족 시 null"). */
    public Optional<ClusterProfileResponse> clusterProfile(PatternAnalysis analysis) {
        if (!analysis.hasCluster()) {
            return Optional.empty();
        }
        return read(analysis.getClusterStats(), ClusterProfileResponse.class);
    }

    public <T> Optional<T> read(JsonNode node, Class<T> type) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.treeToValue(node, type));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
