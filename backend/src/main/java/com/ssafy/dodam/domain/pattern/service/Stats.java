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

    /**
     * 분위 격자로 값의 백분위(0~100)를 구한다(632). {@code grid}는 0~100 오름차순 백분위점, {@code quantiles}는 그 점의 값(비감소).
     * 격자 사이는 선형 보간한다. 같은 값이 여러 점에 걸친 평탄 구간(예: 저축률 0이 표본 40%)에 값이 놓이면 그 구간의 가운데 백분위를
     * 돌려준다 — 끝점을 택하면 "0원 저축이 상위 40%"처럼 한쪽으로 치우친 문장이 나온다. 격자 밖은 0 또는 100. 격자가 없으면 null.
     */
    static Double percentileRank(double[] grid, double[] quantiles, double value) {
        if (grid == null || quantiles == null || grid.length < 2 || grid.length != quantiles.length) {
            return null;
        }
        int last = quantiles.length - 1;
        if (value <= quantiles[0]) {
            return value < quantiles[0] ? 0.0 : plateauMidpoint(grid, quantiles, value);
        }
        if (value >= quantiles[last]) {
            return value > quantiles[last] ? 100.0 : plateauMidpoint(grid, quantiles, value);
        }
        int lo = 0;
        while (lo < last && quantiles[lo + 1] < value) {
            lo++;
        }
        // quantiles[lo] < value <= quantiles[lo + 1]
        if (quantiles[lo + 1] == value) {
            return plateauMidpoint(grid, quantiles, value);
        }
        double span = quantiles[lo + 1] - quantiles[lo];
        double fraction = span <= 0 ? 0.0 : (value - quantiles[lo]) / span;
        return round1(grid[lo] + (grid[lo + 1] - grid[lo]) * fraction);
    }

    private static double plateauMidpoint(double[] grid, double[] quantiles, double value) {
        int first = 0;
        while (first < quantiles.length && quantiles[first] < value) {
            first++;
        }
        int lastIndex = quantiles.length - 1;
        while (lastIndex > first && quantiles[lastIndex] > value) {
            lastIndex--;
        }
        return round1((grid[first] + grid[lastIndex]) / 2.0);
    }

    static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
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
