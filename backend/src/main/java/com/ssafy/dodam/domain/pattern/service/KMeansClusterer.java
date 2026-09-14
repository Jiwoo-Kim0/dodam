package com.ssafy.dodam.domain.pattern.service;

import java.util.List;
import java.util.Random;

/**
 * 표준화 K-Means. 외부 라이브러리 없이 결정적으로 돈다(같은 입력·같은 seed → 같은 결과).
 *
 * <p>차원마다 z-score 표준화 후 유클리드 거리로 군집을 나눈다. 표준화하지 않으면 상한이 3.0인 소비/소득 비율이 상한 1.0인
 * 비중 특징을 압도한다. 초기 중심은 k-means++ 방식으로 seed 고정 {@link Random}에서 뽑고, 배정이 바뀌지 않으면 멈춘다.
 * 비어 버린 군집의 중심은 직전 위치를 유지한다.
 */
public final class KMeansClusterer {

    public static final int MAX_ITERATIONS = 100;
    static final long DEFAULT_SEED = 20260914L;

    private final long seed;

    public KMeansClusterer() {
        this(DEFAULT_SEED);
    }

    public KMeansClusterer(long seed) {
        this.seed = seed;
    }

    public Result cluster(List<double[]> vectors, int k) {
        if (vectors.isEmpty()) {
            throw new IllegalArgumentException("표본이 없습니다.");
        }
        if (k < 1 || k > vectors.size()) {
            throw new IllegalArgumentException("군집 수는 1 이상, 표본 수 이하여야 합니다.");
        }
        int dimension = vectors.get(0).length;
        double[] means = new double[dimension];
        double[] stds = new double[dimension];
        for (int d = 0; d < dimension; d++) {
            double[] column = new double[vectors.size()];
            for (int i = 0; i < vectors.size(); i++) {
                column[i] = vectors.get(i)[d];
            }
            means[d] = Stats.mean(column);
            double std = Stats.std(column);
            stds[d] = std > 0 ? std : 1.0;
        }
        double[][] points = new double[vectors.size()][];
        for (int i = 0; i < vectors.size(); i++) {
            points[i] = standardize(vectors.get(i), means, stds);
        }

        double[][] centroids = initialize(points, k);
        int[] assignments = new int[points.length];
        java.util.Arrays.fill(assignments, -1);
        int iterations = 0;
        while (iterations < MAX_ITERATIONS) {
            iterations++;
            boolean changed = false;
            for (int i = 0; i < points.length; i++) {
                int nearest = nearest(points[i], centroids);
                if (nearest != assignments[i]) {
                    assignments[i] = nearest;
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
            double[][] sums = new double[k][dimension];
            int[] counts = new int[k];
            for (int i = 0; i < points.length; i++) {
                counts[assignments[i]]++;
                for (int d = 0; d < dimension; d++) {
                    sums[assignments[i]][d] += points[i][d];
                }
            }
            for (int c = 0; c < k; c++) {
                if (counts[c] == 0) {
                    continue;
                }
                for (int d = 0; d < dimension; d++) {
                    centroids[c][d] = sums[c][d] / counts[c];
                }
            }
        }
        return new Result(centroids, assignments, means, stds, iterations);
    }

    private double[][] initialize(double[][] points, int k) {
        Random random = new Random(seed);
        double[][] centroids = new double[k][];
        centroids[0] = points[random.nextInt(points.length)].clone();
        double[] nearestDistance = new double[points.length];
        for (int c = 1; c < k; c++) {
            double total = 0.0;
            for (int i = 0; i < points.length; i++) {
                double best = Double.MAX_VALUE;
                for (int j = 0; j < c; j++) {
                    best = Math.min(best, squaredDistance(points[i], centroids[j]));
                }
                nearestDistance[i] = best;
                total += best;
            }
            int chosen;
            if (total <= 0.0) {
                chosen = random.nextInt(points.length);
            } else {
                double target = random.nextDouble() * total;
                chosen = points.length - 1;
                double cumulative = 0.0;
                for (int i = 0; i < points.length; i++) {
                    cumulative += nearestDistance[i];
                    if (cumulative >= target) {
                        chosen = i;
                        break;
                    }
                }
            }
            centroids[c] = points[chosen].clone();
        }
        return centroids;
    }

    static int nearest(double[] point, double[][] centroids) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int c = 0; c < centroids.length; c++) {
            double distance = squaredDistance(point, centroids[c]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = c;
            }
        }
        return best;
    }

    static double squaredDistance(double[] a, double[] b) {
        double sum = 0.0;
        for (int d = 0; d < a.length; d++) {
            sum += (a[d] - b[d]) * (a[d] - b[d]);
        }
        return sum;
    }

    static double[] standardize(double[] raw, double[] means, double[] stds) {
        double[] point = new double[raw.length];
        for (int d = 0; d < raw.length; d++) {
            point[d] = (raw[d] - means[d]) / stds[d];
        }
        return point;
    }

    /**
     * @param centroids   표준화 공간의 군집 중심 [k][d]
     * @param assignments 표본별 군집 번호
     * @param means       차원별 평균(표준화 기준)
     * @param stds        차원별 표준편차(0이면 1로 대체됨)
     * @param iterations  수렴까지 반복 횟수
     */
    public record Result(double[][] centroids, int[] assignments, double[] means, double[] stds, int iterations) {
        public int clusterCount() {
            return centroids.length;
        }
    }
}
