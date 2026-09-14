package com.ssafy.dodam.domain.pattern.service;

import java.util.Arrays;

/** 패턴 분석이 쓰는 작은 통계 함수. 표본이 비면 NaN 대신 0 또는 null을 돌려 호출자가 분기하게 한다. */
final class Stats {

    private Stats() {
    }

    static double mean(double[] values) {
        if (values.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    /** 모표준편차. 군집 범위의 scale과 표준화에 쓴다. */
    static double std(double[] values) {
        if (values.length == 0) {
            return 0.0;
        }
        double mean = mean(values);
        double sum = 0.0;
        for (double value : values) {
            sum += (value - mean) * (value - mean);
        }
        return Math.sqrt(sum / values.length);
    }

    /** 변동계수(std / mean). 평균이 0이거나 표본이 2개 미만이면 null. */
    static Double coefficientOfVariation(double[] values) {
        if (values.length < 2) {
            return null;
        }
        double mean = mean(values);
        if (mean <= 0.0) {
            return null;
        }
        return std(values) / mean;
    }

    /** 선형 보간 분위수(0~1). 정렬은 여기서 한다. */
    static double percentile(double[] values, double p) {
        if (values.length == 0) {
            return 0.0;
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        if (sorted.length == 1) {
            return sorted[0];
        }
        double position = p * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        double fraction = position - lower;
        return sorted[lower] + (sorted[upper] - sorted[lower]) * fraction;
    }

    static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    static Double ratio(double numerator, double denominator) {
        if (denominator <= 0.0) {
            return null;
        }
        return numerator / denominator;
    }

    static double round4(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    static Double round4(Double value) {
        return value == null ? null : round4(value.doubleValue());
    }
}
