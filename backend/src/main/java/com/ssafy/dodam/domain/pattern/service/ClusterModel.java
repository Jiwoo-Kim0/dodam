package com.ssafy.dodam.domain.pattern.service;

import com.ssafy.dodam.domain.user.entity.Gender;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 학습이 끝난 소비 유형 군집 모델. 표준화 파라미터·중심점·군집별 기준 범위·라벨을 함께 들고 있어 배정과 근거 생성이
 * 같은 모델 버전 안에서 이뤄진다(기획안 "모든 군집 기반 판정에는 모델 버전·표본 수·기준 기간을 함께 기록").
 *
 * <p>모집단 통계만 있고 개별 사용자 벡터는 들고 있지 않다.
 *
 * <p>632(v2)부터 <b>분위 격자</b>가 붙는다. 군집별·동년배 집단별·전체 모집단의 특징 분위(5% 간격 21점)로 "군집 안에서 내 위치",
 * "같은 나이대 안에서 내 위치"를 계산한다. v1 아티팩트(격자 없음)는 {@link #supportsPercentiles()}가 false이고 위치 계산은 null이다.
 *
 * @param modelVersion   `spending-kmeans-v1` / `spending-kmeans-v2`
 * @param window         학습에 쓴 기간(= 특징 산출 기간)
 * @param populationSize 학습 표본 수(특징을 만들 수 있었던 사용자 수)
 * @param means          차원별 평균(표준화)
 * @param stds           차원별 표준편차(표준화)
 * @param centroids      표준화 공간의 중심 [k][d]
 * @param clusters       군집 번호 순 요약
 * @param trainedAt      학습 시각
 * @param source         ARTIFACT(Python 학습 아티팩트) | IN_PROCESS(Java 인프로세스 학습)
 * @param trainedBy      학습 도구(예: python-sklearn-1.9.0, java-kmeans)
 * @param quantileGrid   분위 격자의 백분위점(0~100 오름차순). v1이면 null
 * @param peerGroups     동년배 집단 통계(나이대·성별·군집 조합). 없으면 빈 목록
 * @param population     전체 모집단 분위·군집 구성비. v1이면 null
 */
public record ClusterModel(
        String modelVersion,
        PatternWindow window,
        int populationSize,
        double[] means,
        double[] stds,
        double[][] centroids,
        List<ClusterSummary> clusters,
        LocalDateTime trainedAt,
        String source,
        String trainedBy,
        double[] quantileGrid,
        List<PeerGroup> peerGroups,
        GroupStats population
) {
    public static final String SOURCE_IN_PROCESS = "IN_PROCESS";
    /** 5% 간격 21점. Python 학습 스크립트와 인프로세스 학습이 같은 격자를 만든다. */
    public static final double[] DEFAULT_QUANTILE_GRID = {
            0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100 };

    public ClusterModel {
        clusters = List.copyOf(clusters);
        peerGroups = peerGroups == null ? List.of() : List.copyOf(peerGroups);
    }

    /** v1 형식(분위 격자 없음). */
    public ClusterModel(String modelVersion, PatternWindow window, int populationSize, double[] means, double[] stds,
            double[][] centroids, List<ClusterSummary> clusters, LocalDateTime trainedAt, String source, String trainedBy) {
        this(modelVersion, window, populationSize, means, stds, centroids, clusters, trainedAt, source, trainedBy, null, List.of(), null);
    }

    public int clusterCount() {
        return centroids.length;
    }

    public ClusterSummary cluster(int clusterId) {
        return clusters.get(clusterId);
    }

    /** 군집·모집단 분위 격자가 있어 위치(백분위)를 계산할 수 있는가(632). */
    public boolean supportsPercentiles() {
        return quantileGrid != null && quantileGrid.length >= 2
                && clusters.stream().allMatch(c -> c.quantiles() != null && !c.quantiles().isEmpty());
    }

    /** 배정 군집 표본 안에서 사용자 값의 백분위(0~100). 격자가 없으면 null. */
    public Double percentileInCluster(int clusterId, ClusterFeature feature, double value) {
        if (!supportsPercentiles()) {
            return null;
        }
        double[] quantiles = cluster(clusterId).quantiles().get(feature);
        return Stats.percentileRank(quantileGrid, quantiles, value);
    }

    /** 집단(동년배·모집단) 안에서 사용자 값의 백분위. 집단이나 격자가 없으면 null. */
    public Double percentileIn(GroupStats group, ClusterFeature feature, double value) {
        if (group == null || quantileGrid == null || group.quantiles() == null) {
            return null;
        }
        return Stats.percentileRank(quantileGrid, group.quantiles().get(feature), value);
    }

    /** 격자의 특정 백분위점 값(예: 25, 50, 75). 점이 격자에 없으면 가장 가까운 점. */
    public Double quantileValue(GroupStats group, ClusterFeature feature, double percent) {
        if (group == null || quantileGrid == null || group.quantiles() == null || group.quantiles().get(feature) == null) {
            return null;
        }
        double[] values = group.quantiles().get(feature);
        int best = 0;
        for (int i = 1; i < quantileGrid.length; i++) {
            if (Math.abs(quantileGrid[i] - percent) < Math.abs(quantileGrid[best] - percent)) {
                best = i;
            }
        }
        return best < values.length ? values[best] : null;
    }

    /**
     * 동년배 집단을 찾는다. 키의 각 성분이 null이면 "그 축으로 나누지 않음"이다(예: 성별 null = 나이대 전체).
     */
    public Optional<PeerGroup> peerGroup(String ageBand, Gender gender, Integer clusterId) {
        return peerGroups.stream()
                .filter(g -> java.util.Objects.equals(g.ageBand(), ageBand)
                        && java.util.Objects.equals(g.gender(), gender == null ? null : gender.name())
                        && java.util.Objects.equals(g.clusterId(), clusterId))
                .findFirst();
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
     * @param quantiles  특징별 분위 격자 값(632, v2). v1이면 빈 맵
     */
    public record ClusterSummary(int clusterId, String label, int sampleSize, Map<ClusterFeature, FeatureStats> stats,
            Map<ClusterFeature, double[]> quantiles) {
        public ClusterSummary {
            stats = Map.copyOf(stats);
            quantiles = quantiles == null ? Map.of() : Map.copyOf(quantiles);
        }

        public ClusterSummary(int clusterId, String label, int sampleSize, Map<ClusterFeature, FeatureStats> stats) {
            this(clusterId, label, sampleSize, stats, Map.of());
        }
    }

    /** 원 단위 특징 통계. p5·p95는 이상치 보정용 기준 범위, min·max는 참고용 실제 범위다. */
    public record FeatureStats(double centroid, double min, double max, double p5, double p95, double mean, double std) {
    }

    public record Assignment(int clusterId, double confidence, double distance, double secondDistance) {
    }

    /**
     * 집단 통계(632). 동년배 집단과 전체 모집단이 같은 형식이다.
     *
     * @param sampleSize   집단 표본 수
     * @param quantiles    특징별 분위 격자 값
     * @param clusterShare 군집 번호 → 이 집단에서의 비율(0~1)
     */
    public record GroupStats(int sampleSize, Map<ClusterFeature, double[]> quantiles, Map<Integer, Double> clusterShare) {
        public GroupStats {
            quantiles = quantiles == null ? Map.of() : Map.copyOf(quantiles);
            clusterShare = clusterShare == null ? Map.of() : Map.copyOf(clusterShare);
        }
    }

    /**
     * 동년배 집단(632). 각 키 성분이 null이면 그 축으로 나누지 않은 집단이다.
     *
     * @param ageBand   나이대({@link AgeBand}) 또는 null
     * @param gender    MALE / FEMALE 또는 null
     * @param clusterId 군집 번호 또는 null
     */
    public record PeerGroup(String ageBand, String gender, Integer clusterId, GroupStats stats) {
    }
}
