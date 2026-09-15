package com.ssafy.dodam.domain.pattern.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.pattern.service.ClusterModel.ClusterSummary;
import com.ssafy.dodam.domain.pattern.service.ClusterModel.FeatureStats;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Python 학습 스크립트(`backend/ml/pattern/train_cluster_model.py`)가 만드는 모델 아티팩트 JSON의 형식.
 *
 * <p>학습은 scikit-learn에서 하고 배정은 Spring에서 한다. 그래서 아티팩트에는 배정에 필요한 것(표준화 파라미터·표준화 공간 중심)과
 * 근거 생성에 필요한 것(군집별 p5~p95·평균·표준편차·라벨·표본 수), 추적에 필요한 것(버전·학습 시각·기간·평가 지표)이 모두 들어간다.
 * {@link #toModel()}이 형식·차원을 검증해 {@link ClusterModel}로 바꾼다. 특징 순서는 {@link ClusterFeature} 선언 순서와 같아야 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClusterModelArtifact(
        String modelVersion,
        String algorithm,
        String trainedAt,
        String trainedBy,
        Window window,
        List<String> features,
        int populationSize,
        int clusterCount,
        JsonNode evaluation,
        Standardization standardization,
        List<List<Double>> centroids,
        List<Cluster> clusters
) {

    public static final String SOURCE = "ARTIFACT";

    public ClusterModel toModel() {
        List<String> expected = ClusterFeature.ordered().stream().map(ClusterFeature::key).toList();
        if (features == null || !features.equals(expected)) {
            throw new IllegalArgumentException("아티팩트 특징 순서가 다릅니다: " + features + " (기대: " + expected + ")");
        }
        if (modelVersion == null || modelVersion.isBlank()) {
            throw new IllegalArgumentException("아티팩트에 modelVersion이 없습니다.");
        }
        if (window == null || window.start() == null || window.end() == null) {
            throw new IllegalArgumentException("아티팩트에 학습 기간이 없습니다.");
        }
        int dimension = expected.size();
        if (standardization == null || size(standardization.means()) != dimension || size(standardization.stds()) != dimension) {
            throw new IllegalArgumentException("표준화 파라미터 차원이 " + dimension + "이 아닙니다.");
        }
        if (centroids == null || centroids.isEmpty() || clusters == null || clusters.size() != centroids.size()
                || (clusterCount != 0 && clusterCount != centroids.size())) {
            throw new IllegalArgumentException("군집 수와 중심·군집 요약 개수가 맞지 않습니다.");
        }
        double[][] centroidArray = new double[centroids.size()][];
        for (int c = 0; c < centroids.size(); c++) {
            if (size(centroids.get(c)) != dimension) {
                throw new IllegalArgumentException("중심 " + c + "의 차원이 " + dimension + "이 아닙니다.");
            }
            centroidArray[c] = centroids.get(c).stream().mapToDouble(Double::doubleValue).toArray();
        }
        double[] means = standardization.means().stream().mapToDouble(Double::doubleValue).toArray();
        double[] stds = standardization.stds().stream().mapToDouble(v -> v > 0 ? v : 1.0).toArray();

        List<ClusterSummary> summaries = new ArrayList<>(clusters.size());
        for (int c = 0; c < clusters.size(); c++) {
            Cluster cluster = clusters.get(c);
            if (cluster.clusterId() != c) {
                throw new IllegalArgumentException("군집 요약은 clusterId 0부터 순서대로여야 합니다.");
            }
            if (cluster.label() == null || cluster.label().isBlank()) {
                throw new IllegalArgumentException("군집 " + c + "에 라벨이 없습니다.");
            }
            Map<ClusterFeature, FeatureStats> stats = new EnumMap<>(ClusterFeature.class);
            for (ClusterFeature feature : ClusterFeature.ordered()) {
                Stats s = cluster.stats() == null ? null : cluster.stats().get(feature.key());
                if (s == null) {
                    throw new IllegalArgumentException("군집 " + c + "에 " + feature.key() + " 통계가 없습니다.");
                }
                stats.put(feature, new FeatureStats(s.centroid(), s.min(), s.max(), s.p5(), s.p95(), s.mean(), s.std()));
            }
            summaries.add(new ClusterSummary(c, cluster.label(), cluster.sampleSize(), stats));
        }
        PatternWindow trainingWindow;
        try {
            trainingWindow = new PatternWindow(YearMonth.parse(window.start()), YearMonth.parse(window.end()));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("학습 기간 형식은 yyyy-MM이어야 합니다.", exception);
        }
        LocalDateTime trained;
        try {
            trained = trainedAt == null ? LocalDateTime.MIN : LocalDateTime.parse(trainedAt);
        } catch (DateTimeParseException exception) {
            trained = LocalDateTime.MIN;
        }
        return new ClusterModel(modelVersion, trainingWindow, populationSize, means, stds, centroidArray, summaries, trained,
                SOURCE, trainedBy == null ? "unknown" : trainedBy);
    }

    private static int size(List<?> list) {
        return list == null ? -1 : list.size();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Window(String start, String end, int months) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Standardization(List<Double> means, List<Double> stds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cluster(int clusterId, String label, int sampleSize, Map<String, Stats> stats) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stats(double centroid, double min, double max, double p5, double p95, double mean, double std) {
    }
}
