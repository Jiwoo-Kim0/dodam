package com.ssafy.dodam.domain.forecast.dto.response;

import com.ssafy.dodam.domain.forecast.service.ForecastFallbackReason;
import com.ssafy.dodam.domain.forecast.service.SpendingForecastCalculator;
import java.time.YearMonth;
import java.util.List;

/**
 * 월별 변동 소비 예측 응답(S15P21D105-706).
 *
 * <p><b>과거 실적을 함께 싣는다.</b> 화면은 과거 실선과 예측 점선을 한 선으로 잇는다. 예측 세 달만 보여 주면 근거 없는 숫자가
 * 되고, 계절성도 작년 같은 달을 같이 봐야 의미가 생긴다. 한 번의 호출로 그릴 수 있게 둘을 같이 준다.
 *
 * <p>{@code lowerBound}·{@code upperBound}는 <b>정확도를 주장하는 값이 아니라 화면 표시용</b>이다. 화면 문구도 "예상 범위"로
 * 둔다. 이 데이터는 시연용 합성 데이터이고 예측 성능은 검증된 바 없다.
 */
public record SpendingForecastResponse(
        String baseMonth,
        int horizonMonths,
        String fallbackReason,
        List<HistoryPoint> history,
        List<ForecastPoint> forecasts
) {

    /** 실제 지출. 거래가 없던 달은 목록에 없다(0이 아니라 결측) */
    public record HistoryPoint(String month, long amount) {
    }

    public record ForecastPoint(String month, long predicted, long lowerBound, long upperBound) {
    }

    public static SpendingForecastResponse of(YearMonth baseMonth, int horizonMonths,
            SpendingForecastCalculator.Result result, List<SpendingForecastCalculator.MonthlyPoint> history) {
        return new SpendingForecastResponse(
                baseMonth.toString(),
                horizonMonths,
                result.fallbackReason() == ForecastFallbackReason.NONE ? null : result.fallbackReason().name(),
                history.stream()
                        .map(point -> new HistoryPoint(point.month().toString(), point.variableSpending()))
                        .toList(),
                result.months().stream()
                        .map(month -> new ForecastPoint(month.month().toString(), month.predicted(),
                                month.lowerBound(), month.upperBound()))
                        .toList());
    }
}
