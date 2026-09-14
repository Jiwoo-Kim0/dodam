package com.ssafy.dodam.domain.pattern.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 학습이 끝난 소비 유형 군집 모델. 표준화 파라미터·중심점·군집별 기준 범위·라벨을 함께 들고 있어 배정과 근거 생성이
 * 같은 모델 버전 안에서 이뤄진다(기획안 "모든 군집 기반 판정에는 모델 버전·표본 수·기준 기간을 함께 기록").
 *
 * <p>모집단 통계만 있고 개별 사용자 벡터는 들고 있지 않다.
 *
 * @param modelVersion   `spending-kmeans-v1`
 * @param window         학습에 쓴 기간(= 특징 산출 기간)
 * @param populationSize 학습 표본 수(특징을 만들 수 있었던 사용자 수)
 * @param means          차원별 평균(표준화)
 * @param stds           차원별 표준편차(표준화)
 * @param centroids      표준화 공간의 중심 [k][d]
 * @param clusters       군집 번호 순 요약
 * @param trainedAt      학습 시각
 */
public record ClusterModel(
        String modelVersion,
        PatternWindow window,
        int populationSize,
        double[] means,
        double[] stds,
        double[][] centroids,
        List<ClusterSummary> clusters,
        LocalDateTime trainedAt
) {

    public ClusterModel {
        clusters = List.copyOf(clusters);
    }

    public int clusterCount() {
        return centroids.length;
    }

    public ClusterSummary cluster(int clusterId) {
        return clusters.get(clusterId);
    }

    /**
     * 가장 가까운 중심에 배정한다. 신뢰도는 (두 번째 거리 − 첫 거리) / 두 번째 거리(0~1)다. 두 중심 사이 정확히 가운데면 0,
     * 첫 중심에 딱 붙어 있으면 1이다. 군집이 하나뿐이면 비교 대상이 없어 1로 둔다.
     */
    public Assignment assign(double[] rawVector) {
        double[] point = KMeansClusterer.standardize(rawVector, means, stds);
        int nearest = -1;
        double first = Double.MAX_VALUE;
        double second = Double.MAX_VALUE;
        for (int c = 0; c < centroids.length; c++) {
            double distance = Math.sqrt(KMeansClusterer.squaredDistance(point, centroids[c]));
            if (distance < first) {
                second = first;
                first = distance;
                nearest = c;
            } else if (distance < second) {
                second = distance;
            }
        }
        double confidence;
        if (centroids.length == 1 || second == Double.MAX_VALUE) {
            confidence = 1.0;
        } else if (second <= 0.0) {
            confidence = first <= 0.0 ? 1.0 : 0.0;
        } else {
            confidence = Stats.clamp01((second - first) / second);
        }
        return new Assignment(nearest, Stats.round4(confidence), first, second);
    }

    /**
     * 기획안 5-2 범위 이탈. 기준 범위는 이상치 보정용 p5~p95, scale = max(p95 − p5, 표준편차). scale이 0이면 이탈을 계산하지 않는다(0).
     */
    public static double rangeDeviation(double value, FeatureStats stats) {
        double scale = Math.max(stats.p95() - stats.p5(), stats.std());
        if (scale <= 0.0) {
            return 0.0;
        }
        if (value < stats.p5()) {
            return (stats.p5() - value) / scale;
        }
        if (value > stats.p95()) {
            return (value - stats.p95()) / scale;
        }
        return 0.0;
    }

    /**
     * @param clusterId  군집 번호(0부터)
     * @param label      {@link ClusterLabelPolicy}가 정한 라벨
     * @param sampleSize 군집 표본 수
     * @param stats      특징별 기준 범위(원 단위)
     */
    public record ClusterSummary(int clusterId, String label, int sampleSize, Map<ClusterFeature, FeatureStats> stats) {
        public ClusterSummary {
            stats = Map.copyOf(stats);
        }
    }

    /** 원 단위 특징 통계. p5·p95는 이상치 보정용 기준 범위, min·max는 참고용 실제 범위다. */
    public record FeatureStats(double centroid, double min, double max, double p5, double p95, double mean, double std) {
    }

    public record Assignment(int clusterId, double confidence, double distance, double secondDistance) {
    }
}
