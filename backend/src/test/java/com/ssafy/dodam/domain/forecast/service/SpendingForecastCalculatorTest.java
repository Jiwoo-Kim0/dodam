package com.ssafy.dodam.domain.forecast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.domain.forecast.service.SpendingForecastCalculator.MonthlyPoint;
import com.ssafy.dodam.domain.forecast.service.SpendingForecastCalculator.Result;
import java.time.Month;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * S15P21D105-706 추정 로직. 기대값은 seed를 설계할 때 쓴 파이썬 계산기가 낸 값이고, 그 값은 로컬 MySQL에 적재한
 * U0003의 24개월을 읽어 다시 확인했다(계획서 7.1). 두 구현이 독립이라 서로의 검산이 된다.
 */
class SpendingForecastCalculatorTest {

    private static MonthlyPoint p(String ym, long amount) {
        return new MonthlyPoint(YearMonth.parse(ym), amount);
    }

    /** 적재된 U0003의 2024-09 ~ 2026-08 변동 소비 월 합계 */
    private static List<MonthlyPoint> u0003History() {
        return List.of(
            p("2024-09", 800000), p("2024-10", 830000), p("2024-11", 850000),
            p("2024-12", 1020000), p("2025-01", 810000), p("2025-02", 740000),
            p("2025-03", 825000), p("2025-04", 800000), p("2025-05", 1085000),
            p("2025-06", 870000), p("2025-07", 905000), p("2025-08", 880000),
            p("2025-09", 940000), p("2025-10", 980000), p("2025-11", 1010000),
            p("2025-12", 1210000), p("2026-01", 960000), p("2026-02", 870000),
            p("2026-03", 974000), p("2026-04", 939000), p("2026-05", 1653000),
            p("2026-06", 1015000), p("2026-07", 1031000), p("2026-08", 994000)        );
    }

    @Test
    @DisplayName("U0003 24개월이면 완만한 증가 추세 위에 12월 상승이 얹힌다")
    void forecastsU0003() {
        Result result = SpendingForecastCalculator.forecast(u0003History(), YearMonth.of(2026, 9), 3);

        assertThat(result.fallbackReason()).isEqualTo(ForecastFallbackReason.NONE);
        assertThat(result.months()).extracting(f -> f.month().toString())
                .containsExactly("2026-10", "2026-11", "2026-12");
        // 파이썬 계산기: 984,923 / 1,017,216 / 1,224,746
        assertThat(result.months().get(0).predicted()).isCloseTo(984_923L, org.assertj.core.data.Offset.offset(100L));
        assertThat(result.months().get(1).predicted()).isCloseTo(1_017_216L, org.assertj.core.data.Offset.offset(100L));
        assertThat(result.months().get(2).predicted()).isCloseTo(1_224_746L, org.assertj.core.data.Offset.offset(100L));
        assertThat(result.trend()).isPositive();
        // 8월 실적 994,000 대비 12월이 20% 이상 높아야 화면에서 연말 상승이 보인다
        assertThat(result.months().get(2).predicted()).isGreaterThan((long) (994_000 * 1.20));
        assertThat(result.seasonalIndex().get(Month.DECEMBER))
                .isGreaterThan(result.seasonalIndex().get(Month.NOVEMBER));
    }

    @Test
    @DisplayName("구간은 예측값을 감싸고 음수로 내려가지 않는다")
    void boundsContainPrediction() {
        Result result = SpendingForecastCalculator.forecast(u0003History(), YearMonth.of(2026, 9), 3);

        assertThat(result.months()).allSatisfy(f -> {
            assertThat(f.lowerBound()).isNotNegative().isLessThanOrEqualTo(f.predicted());
            assertThat(f.upperBound()).isGreaterThanOrEqualTo(f.predicted());
        });
    }

    @Test
    @DisplayName("MAD가 0이어도 클리핑이 추세를 죽이지 않는다")
    void keepsTrendWhenMadIsZero() {
        // 절반이 넘는 달이 100,000이라 중앙값 100,000, 편차의 중앙값 0 -> MAD = 0
        List<MonthlyPoint> history = List.of(
                p("2026-01", 100_000), p("2026-02", 100_000), p("2026-03", 100_000), p("2026-04", 100_000),
                p("2026-05", 150_000), p("2026-06", 200_000), p("2026-07", 250_000));

        Result result = SpendingForecastCalculator.forecast(history, YearMonth.of(2026, 7), 1);

        // 가드가 없으면 모든 달이 100,000으로 눌려 추세가 0이 되고 수준도 100,000에 고정된다
        assertThat(result.trend()).isPositive();
        assertThat(result.level()).isGreaterThan(100_000);
        assertThat(result.months().get(0).predicted()).isGreaterThan(100_000L);
    }

    @Test
    @DisplayName("결측 월은 위치가 아니라 개월 수로 센다")
    void measuresTrendInMonthsNotPositions() {
        List<MonthlyPoint> dense = List.of(
                p("2026-01", 100_000), p("2026-02", 200_000), p("2026-03", 300_000), p("2026-04", 400_000));
        List<MonthlyPoint> sparse = List.of(
                p("2026-01", 100_000), p("2026-03", 200_000), p("2026-05", 300_000), p("2026-07", 400_000));

        double denseTrend = SpendingForecastCalculator.forecast(dense, YearMonth.of(2026, 4), 1).trend();
        double sparseTrend = SpendingForecastCalculator.forecast(sparse, YearMonth.of(2026, 7), 1).trend();

        assertThat(denseTrend).isCloseTo(100_000, org.assertj.core.data.Offset.offset(1.0));
        assertThat(sparseTrend).isCloseTo(50_000, org.assertj.core.data.Offset.offset(1.0));
    }

    @Test
    @DisplayName("12개월보다 짧으면 계절 계수를 만들지 않는다")
    void skipsSeasonalityWhenHistoryIsShort() {
        List<MonthlyPoint> history = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            history.add(p(YearMonth.of(2026, 1).plusMonths(i).toString(), 100_000 + i * 1_000L));
        }

        Result result = SpendingForecastCalculator.forecast(history, YearMonth.of(2026, 6), 2);

        assertThat(result.fallbackReason()).isEqualTo(ForecastFallbackReason.SEASONALITY_UNAVAILABLE);
        assertThat(result.seasonalIndex()).isEmpty();
        assertThat(result.months()).hasSize(2);
    }

    @Test
    @DisplayName("관측이 1회뿐인 달은 금액이 튀어도 다른 1회 관측 달과 같은 계수를 받는다")
    void singleObservationMonthGetsNeutralFactor() {
        List<MonthlyPoint> history = new ArrayList<>();
        for (int i = 0; i < 13; i++) {                       // 2025-01 ~ 2026-01: 1월만 2회, 나머지는 1회
            YearMonth month = YearMonth.of(2025, 1).plusMonths(i);
            history.add(new MonthlyPoint(month, month.getMonthValue() == 7 ? 3_000_000L : 1_000_000L));
        }

        Result result = SpendingForecastCalculator.forecast(history, YearMonth.of(2026, 1), 1);

        // 7월이 3배로 튀었지만 관측이 한 번뿐이라 계절로 굳지 않는다
        assertThat(result.seasonalIndex().get(Month.JULY))
                .isEqualTo(result.seasonalIndex().get(Month.FEBRUARY));
        assertThat(result.seasonalIndex().get(Month.JANUARY))
                .isLessThan(result.seasonalIndex().get(Month.JULY));
    }

    @Test
    @DisplayName("이력이 3개월보다 짧으면 예측하지 않는다")
    void refusesShortHistory() {
        Result result = SpendingForecastCalculator.forecast(
                List.of(p("2026-01", 100_000), p("2026-02", 110_000)), YearMonth.of(2026, 2), 3);

        assertThat(result.fallbackReason()).isEqualTo(ForecastFallbackReason.USER_DATA_TOO_SHORT);
        assertThat(result.months()).isEmpty();
    }

    @Test
    @DisplayName("관측이 2개 미만이면 특징을 만들 수 없다")
    void refusesSinglePoint() {
        assertThat(SpendingForecastCalculator.forecast(List.of(p("2026-01", 100_000)), YearMonth.of(2026, 1), 3)
                .fallbackReason()).isEqualTo(ForecastFallbackReason.FEATURES_UNAVAILABLE);
        assertThat(SpendingForecastCalculator.forecast(null, YearMonth.of(2026, 1), 3)
                .fallbackReason()).isEqualTo(ForecastFallbackReason.FEATURES_UNAVAILABLE);
    }

    @Test
    @DisplayName("누적 감쇠는 단조 증가하며 phi/(1-phi)로 수렴한다")
    void dampingConvergesMonotonically() {
        double previous = 0;
        for (long h = 1; h <= 200; h++) {
            double current = SpendingForecastCalculator.damping(h);
            assertThat(current).isGreaterThan(previous);
            previous = current;
        }
        double limit = SpendingForecastCalculator.DAMPING / (1 - SpendingForecastCalculator.DAMPING);
        assertThat(previous).isCloseTo(limit, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(previous).isLessThan(limit);
    }

    @Test
    @DisplayName("horizon이 0 이하면 거부한다")
    void rejectsNonPositiveHorizon() {
        assertThatThrownBy(() -> SpendingForecastCalculator.forecast(u0003History(), YearMonth.of(2026, 9), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
