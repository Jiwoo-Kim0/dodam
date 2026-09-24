package com.ssafy.dodam.domain.forecast.service;

import java.time.Month;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 월별 변동 소비 예측(S15P21D105-706). 계절 계수·수준·추세를 모두 <b>그 사용자의 이력만으로</b> 추정한다.
 *
 * <p><b>왜 학습한 아티팩트를 쓰지 않는가.</b> 처음 설계는 군집별 계절 계수를 JSON에 담아 배포하고 런타임이 군집 키로 조회하는
 * 구조였다. 그러려면 같은 군집 1,000명이 같은 계절 모양을 가져야 하는데 시연 대상은 한 명이라, 나머지 999명에게 같은 모양을 심는
 * 작업이 순전히 아티팩트를 채우기 위한 일이 된다. 사용자 단위 추정은 보통 표본이 모자라 못 쓰지만(계수 12개를 2주기로 추정),
 * 시연 데이터는 월 합계가 결정적이라 2주기면 정확히 복원된다. 대신 군집 키 설계와 두 아티팩트의 버전 결합이 통째로 사라진다.
 * <b>실제 거래가 들어오면 노이즈 때문에 2주기로는 흔들리므로 인구 단위 학습으로 되돌려야 한다.</b>
 *
 * <p>계산 순서는 계절 추정 → 계절 제거 → 이상치 클리핑 → 지수평활(수준) → Theil-Sen(추세) → 감쇠 투영 → 구간이다.
 * 구간은 정확도를 주장하는 값이 아니라 화면 표시용이다.
 *
 * <p>입력은 <b>거래가 있는 달만</b> 온다. 빠진 달은 0이 아니라 결측이며, 0을 관측치로 넣으면 수준이 끌려 내려가 휴면 구간이 있는
 * 사용자의 예측이 실제의 절반 이하가 된다. 추세는 위치가 아니라 <b>첫 달로부터의 개월 수</b>로 잰다. 위치로 재면 결측이 있는
 * 사용자의 기울기가 실제보다 가팔라진다.
 */
public final class SpendingForecastCalculator {

    /** 지수평활 계수. 1에 가까울수록 최근 달을 더 믿는다 */
    static final double ALPHA = 0.5;
    /** 추세 감쇠. 누적 감쇠 D(h)가 φ/(1−φ)로 수렴해 먼 달의 추세가 발산하지 않는다 */
    static final double DAMPING = 0.85;
    /** 80% 양측 구간 */
    static final double Z = 1.28;
    /** MAD를 정규분포 표준편차로 환산하는 상수 */
    static final double MAD_TO_SIGMA = 1.4826;
    /** 클리핑 폭(MAD 배수) */
    static final int CLIP_K = 3;
    /** 이보다 짧으면 예측하지 않는다 */
    static final int MIN_MONTHS = 3;
    /** 이보다 짧으면 계절 계수를 만들지 않는다 */
    static final int MIN_MONTHS_FOR_SEASONALITY = 12;

    private SpendingForecastCalculator() {
    }

    /** 한 달의 변동 소비 관측치. 거래가 없던 달은 이 목록에 없다 */
    public record MonthlyPoint(YearMonth month, long variableSpending) {
    }

    /** 한 달의 예측. 금액은 원 단위 정수다 */
    public record MonthlyForecast(YearMonth month, long predicted, long lowerBound, long upperBound) {
    }

    /**
     * @param fallbackReason 축약 경로를 탔다면 그 사유. {@link ForecastFallbackReason#NONE}이면 전체 절차를 돌았다
     * @param months         예측 결과. 사유가 USER_DATA_TOO_SHORT / FEATURES_UNAVAILABLE이면 비어 있다
     * @param seasonalIndex  추정한 달력 월별 계수(기하평균 1). 계절을 못 만들면 비어 있다
     * @param level          계절을 뺀 축의 최신 수준
     * @param trend          계절을 뺀 축의 월당 기울기
     */
    public record Result(ForecastFallbackReason fallbackReason, List<MonthlyForecast> months,
            Map<Month, Double> seasonalIndex, double level, double trend) {
        public Result {
            months = List.copyOf(months);
            seasonalIndex = Map.copyOf(seasonalIndex);
        }

        static Result empty(ForecastFallbackReason reason) {
            return new Result(reason, List.of(), Map.of(), 0, 0);
        }
    }

    /**
     * @param history       거래가 있는 달만. 순서는 상관없다
     * @param baseMonth     기준 달(보통 오늘이 속한 달). 예측은 그 다음 달부터 시작한다
     * @param horizonMonths 예측할 달 수
     */
    public static Result forecast(List<MonthlyPoint> history, YearMonth baseMonth, int horizonMonths) {
        if (horizonMonths < 1) {
            throw new IllegalArgumentException("horizonMonths는 1 이상이어야 합니다: " + horizonMonths);
        }
        if (history == null || history.size() < 2) {
            return Result.empty(ForecastFallbackReason.FEATURES_UNAVAILABLE);
        }
        List<MonthlyPoint> points = new ArrayList<>(history);
        points.sort(Comparator.comparing(MonthlyPoint::month));
        if (points.size() < MIN_MONTHS) {
            return Result.empty(ForecastFallbackReason.USER_DATA_TOO_SHORT);
        }

        boolean seasonal = points.size() >= MIN_MONTHS_FOR_SEASONALITY;
        Map<Month, Double> index = seasonal ? seasonalIndex(points) : Map.of();
        double[] adjusted = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            adjusted[i] = points.get(i).variableSpending() / factor(index, points.get(i).month().getMonth());
        }
        clipOutliers(adjusted);

        double level = adjusted[0];
        for (int i = 1; i < adjusted.length; i++) {
            level = ALPHA * adjusted[i] + (1 - ALPHA) * level;
        }
        double trend = theilSen(points, adjusted);
        double sigma = residualDeviation(adjusted, level);

        YearMonth last = points.get(points.size() - 1).month();
        List<MonthlyForecast> out = new ArrayList<>(horizonMonths);
        for (int k = 1; k <= horizonMonths; k++) {
            YearMonth target = baseMonth.plusMonths(k);
            long h = ChronoUnit.MONTHS.between(last, target);
            double base = Math.max(0, level + damping(h) * trend);
            double f = factor(index, target.getMonth());
            double spread = Z * sigma * Math.sqrt(h);
            out.add(new MonthlyForecast(target,
                    Math.round(base * f),
                    Math.round(Math.max(0, base - spread) * f),
                    Math.round(Math.max(0, base + spread) * f)));
        }
        return new Result(seasonal ? ForecastFallbackReason.NONE : ForecastFallbackReason.SEASONALITY_UNAVAILABLE,
                out, index, level, trend);
    }

    /**
     * 달력 월별 계수. 각 달의 관측치를 전체 평균으로 나눈 비율의 중앙값이며 기하평균이 1이 되게 맞춘다.
     *
     * <p>관측이 1회뿐인 달은 1로 둔다. 한 번 본 값을 그 달의 성질로 확정하면 우연한 한 달이 매년 반복되는 계절로 굳는다.
     */
    private static Map<Month, Double> seasonalIndex(List<MonthlyPoint> points) {
        double mean = points.stream().mapToLong(MonthlyPoint::variableSpending).average().orElse(0);
        if (mean <= 0) {
            return Map.of();
        }
        Map<Month, List<Double>> byMonth = new EnumMap<>(Month.class);
        for (MonthlyPoint p : points) {
            byMonth.computeIfAbsent(p.month().getMonth(), m -> new ArrayList<>())
                    .add(p.variableSpending() / mean);
        }
        Map<Month, Double> index = new EnumMap<>(Month.class);
        for (Map.Entry<Month, List<Double>> entry : byMonth.entrySet()) {
            index.put(entry.getKey(), entry.getValue().size() < 2 ? 1.0 : median(entry.getValue()));
        }
        double logSum = 0;
        for (double value : index.values()) {
            logSum += Math.log(value);
        }
        double geometricMean = Math.exp(logSum / index.size());
        index.replaceAll((month, value) -> value / geometricMean);
        return index;
    }

    /**
     * MAD 기반 클리핑. <b>MAD가 0이면 건너뛴다</b> — 구간이 한 점으로 붕괴해 모든 달이 중앙값으로 눌리고 추세가 0이 된다.
     * 거래가 적은 사용자에서 나올 수 있고, 증상이 "추세 없음"으로만 보여 원인을 찾기 어렵다.
     */
    private static void clipOutliers(double[] values) {
        double med = median(values);
        double[] deviations = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            deviations[i] = Math.abs(values[i] - med);
        }
        double mad = median(deviations);
        if (mad <= 0) {
            return;
        }
        double width = CLIP_K * MAD_TO_SIGMA * mad;
        for (int i = 0; i < values.length; i++) {
            values[i] = Math.min(Math.max(values[i], med - width), med + width);
        }
    }

    /** 모든 점쌍 기울기의 중앙값. 최소제곱은 남은 이상치 하나에도 끌린다 */
    private static double theilSen(List<MonthlyPoint> points, double[] adjusted) {
        YearMonth first = points.get(0).month();
        List<Double> slopes = new ArrayList<>();
        for (int i = 0; i < adjusted.length; i++) {
            long xi = ChronoUnit.MONTHS.between(first, points.get(i).month());
            for (int j = i + 1; j < adjusted.length; j++) {
                long xj = ChronoUnit.MONTHS.between(first, points.get(j).month());
                if (xj != xi) {
                    slopes.add((adjusted[j] - adjusted[i]) / (double) (xj - xi));
                }
            }
        }
        return slopes.isEmpty() ? 0 : median(slopes);
    }

    private static double residualDeviation(double[] values, double level) {
        double sum = 0;
        for (double value : values) {
            sum += (value - level) * (value - level);
        }
        return Math.sqrt(sum / values.length);
    }

    /** 누적 감쇠 D(h) = φ + φ² + … + φ^h. h가 커져도 φ/(1−φ)로 단조 수렴한다 */
    static double damping(long h) {
        return DAMPING * (1 - Math.pow(DAMPING, h)) / (1 - DAMPING);
    }

    private static double factor(Map<Month, Double> index, Month month) {
        return index.getOrDefault(month, 1.0);
    }

    private static double median(List<Double> values) {
        double[] array = new double[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = values.get(i);
        }
        return median(array);
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }
}
