package com.ssafy.dodam.domain.pattern.service;

import com.ssafy.dodam.domain.pattern.entity.AnalysisMode;
import com.ssafy.dodam.domain.pattern.service.ClusterModel.Assignment;
import com.ssafy.dodam.domain.pattern.service.ClusterModel.ClusterSummary;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 사용자 특징을 군집에 배정하고 노출 조건을 검사한다(518 Fallback).
 *
 * <p>기획안 노출 조건(표본 수·사용자 데이터 3개월·신뢰도)을 모두 충족해야 군집 결과를 돌려준다. 하나라도 어긋나면 사유와 함께
 * 비어 있는 결과를 주고, 호출자는 규칙 지표만으로 분석을 완료한다. 모델 예외는 분석 실패로 번지지 않게 여기서 잡아
 * {@link FallbackReason#MODEL_FAILURE}로 바꾼다(명세 "모델 실패 시 RULE_ONLY 자동 전환").
 */
@Component
public class ClusterAssigner {

    private static final Logger log = LoggerFactory.getLogger(ClusterAssigner.class);

    private final ClusterModelProvider provider;
    private final PatternProperties properties;

    public ClusterAssigner(ClusterModelProvider provider, PatternProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    public Outcome evaluate(AnalysisMode requestedMode, PatternFeatures features, boolean forceRefresh) {
        if (requestedMode == AnalysisMode.RULE_ONLY) {
            return Outcome.fallback(FallbackReason.RULE_ONLY_REQUESTED, null);
        }
        if (features.monthsWithData() < properties.minUserMonths()) {
            return Outcome.fallback(FallbackReason.USER_DATA_TOO_SHORT, null);
        }
        if (!features.hasClusterVector()) {
            return Outcome.fallback(FallbackReason.FEATURES_UNAVAILABLE, null);
        }
        ClusterModelProvider.Lookup lookup;
        try {
            lookup = provider.lookup(features.window(), forceRefresh);
        } catch (RuntimeException failure) {
            log.warn("Pattern cluster model unavailable, falling back to RULE_ONLY", failure);
            return Outcome.fallback(FallbackReason.MODEL_FAILURE, null);
        }
        if (!lookup.hasModel()) {
            return Outcome.fallback(FallbackReason.INSUFFICIENT_POPULATION, lookup.populationSize());
        }
        ClusterModel model = lookup.model();
        Assignment assignment;
        try {
            assignment = model.assign(features.clusterVector());
        } catch (RuntimeException failure) {
            log.warn("Pattern cluster assignment failed, falling back to RULE_ONLY", failure);
            return Outcome.fallback(FallbackReason.MODEL_FAILURE, lookup.populationSize());
        }
        ClusterSummary summary = model.cluster(assignment.clusterId());
        if (summary.sampleSize() < properties.minClusterSampleSize()) {
            return Outcome.fallback(FallbackReason.SMALL_CLUSTER_SAMPLE, lookup.populationSize());
        }
        if (assignment.confidence() < properties.minConfidence()) {
            return Outcome.fallback(FallbackReason.LOW_CONFIDENCE, lookup.populationSize());
        }
        return new Outcome(new ClusterResult(model, summary, assignment, features.clusterFeatureValues()), null,
                lookup.populationSize());
    }

    /**
     * @param cluster        배정 결과. 조건 미충족이면 null
     * @param fallbackReason 군집 결과가 없는 사유. 배정됐으면 null
     * @param populationSize 모델 학습 표본 수. 모델까지 가지 못했으면 null
     */
    public record Outcome(ClusterResult cluster, FallbackReason fallbackReason, Integer populationSize) {

        static Outcome fallback(FallbackReason reason, Integer populationSize) {
            return new Outcome(null, reason, populationSize);
        }

        public boolean assigned() {
            return cluster != null;
        }
    }

    public record ClusterResult(
            ClusterModel model,
            ClusterSummary summary,
            Assignment assignment,
            Map<ClusterFeature, Double> userValues
    ) {
    }
}
