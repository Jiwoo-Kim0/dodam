package com.ssafy.dodam.domain.forecast.dto.response;

import com.ssafy.dodam.domain.forecast.service.ForecastFallbackReason;
import com.ssafy.dodam.domain.forecast.service.SpendingForecastCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 월별 변동 소비 예측 응답(S15P21D105-706, 근거 분해 S15P21D105-707).
 *
 * <p><b>과거 실적을 함께 싣는다.</b> 화면은 과거 실선과 예측 점선을 한 선으로 잇는다. 예측 세 달만 보여 주면 근거 없는 숫자가
 * 되고, 계절성도 작년 같은 달을 같이 봐야 의미가 생긴다. 한 번의 호출로 그릴 수 있게 둘을 같이 준다.
 *
 * <p>{@code lowerBound}·{@code upperBound}는 <b>정확도를 주장하는 값이 아니라 화면 표시용</b>이다. 화면 문구도 "예상 범위"로
 * 둔다. 이 데이터는 시연용 합성 데이터이고 예측 성능은 검증된 바 없다.
 *
 * <p><b>근거 분해(707)</b>: 각 예측 달은 {@code predicted = explanation.level + trendEffect + seasonalEffect}가 원 단위로
 * 정확히 성립한다. 반올림 오차를 {@code seasonalEffect}가 흡수하도록 두 효과를 차이로 정의했다. 화면과 코치가 세 조각을 더해
 * 보여 줄 때 합계가 {@code predicted}와 어긋나지 않게 하기 위해서다.
 *
 * @param explanation 예측이 없으면(이력 부족) null
 */
public record SpendingForecastResponse(
        String baseMonth,
        int horizonMonths,
        String fallbackReason,
        Explanation explanation,
        List<HistoryPoint> history,
        List<ForecastPoint> forecasts
) {

    /**
     * 예측 전체에 공통인 근거.
     *
     * @param level              계절을 뺀 최근 월 소비 수준(지수평활). "평소 한 달 소비"로 읽는다
     * @param monthlyTrend       계절을 뺀 축의 월당 기울기(Theil-Sen). 감쇠 전 값이라 먼 달일수록 실제 반영분은 작다
     * @param seasonalityApplied 계절 계수를 썼는지. 이력이 12개월 미만이면 false
     */
    public record Explanation(long level, long monthlyTrend, boolean seasonalityApplied) {
    }

    /** 실제 지출. 거래가 없던 달은 목록에 없다(0이 아니라 결측) */
    public record HistoryPoint(String month, long amount) {
    }

    /**
     * @param trendEffect    수준에 더해진 추세분 = 계절 적용 전 값 − 수준. 감쇠가 들어가 있다
     * @param seasonalEffect 계절 계수로 더해진(빠진) 금액 = predicted − 계절 적용 전 값
     * @param seasonalFactor 이 달의 계절 계수(소수 2자리). 계절을 쓰지 않았으면 null
     * @param lastYearAmount 1년 전 같은 달 실적. 추정 입력 24개월 안에 거래가 없었으면 null
     */
    public record ForecastPoint(String month, long predicted, long lowerBound, long upperBound, long trendEffect,
            long seasonalEffect, BigDecimal seasonalFactor, Long lastYearAmount) {
    }

    /**
     * @param inputHistory 추정 입력 전체(24개월). 작년 같은 달 실적을 찾는 데 쓴다
     * @param chartHistory 화면에 실을 최근 구간
     */
    public static SpendingForecastResponse of(YearMonth baseMonth, int horizonMonths,
            SpendingForecastCalculator.Result result, List<SpendingForecastCalculator.MonthlyPoint> inputHistory,
            List<SpendingForecastCalculator.MonthlyPoint> chartHistory) {
        boolean seasonal = !result.seasonalIndex().isEmpty();
        long level = Math.round(result.level());
        Map<YearMonth, Long> actualByMonth = inputHistory.stream().collect(Collectors.toMap(
                SpendingForecastCalculator.MonthlyPoint::month, SpendingForecastCalculator.MonthlyPoint::variableSpending,
                Long::sum));
        Function<SpendingForecastCalculator.MonthlyForecast, ForecastPoint> toPoint = month -> new ForecastPoint(
                month.month().toString(), month.predicted(), month.lowerBound(), month.upperBound(),
                month.baseline() - level,
                month.predicted() - month.baseline(),
                seasonal ? BigDecimal.valueOf(month.seasonalFactor()).setScale(2, RoundingMode.HALF_UP) : null,
                actualByMonth.get(month.month().minusYears(1)));

        return new SpendingForecastResponse(
                baseMonth.toString(),
                horizonMonths,
                result.fallbackReason() == ForecastFallbackReason.NONE ? null : result.fallbackReason().name(),
                result.months().isEmpty() ? null : new Explanation(level, Math.round(result.trend()), seasonal),
                chartHistory.stream()
                        .map(point -> new HistoryPoint(point.month().toString(), point.variableSpending()))
                        .toList(),
                result.months().stream().map(toPoint).toList());
    }
}
