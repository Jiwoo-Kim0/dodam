package com.ssafy.dodam.domain.cashflow.dto.response;

import com.ssafy.dodam.domain.cashflow.entity.CashflowForecast;
import com.ssafy.dodam.domain.cashflow.entity.ForecastStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 예측 헤더 응답.
 *
 * <p>`endDate`는 저장하지 않고 `baseDate + horizonDays - 1`로 파생한다(538의
 * {@code CashflowForecastPeriod}와 같은 규칙). 양 끝을 포함하는 구간이다.
 *
 * <p>`requestHash`는 캐시·멱등 판정용 내부 지문이므로 응답에 넣지 않는다.
 *
 * <p>`availableToday`는 S15P21D105-542에서 계산 정의를 확정하고 추가했다(`max(0, minimumBalance)`).
 * 542 이전에 완료된 예측에는 값이 없어 null이며, 0원으로 채우지 않는다.
 */
public record CashflowForecastResponse(
        Long forecastId,
        LocalDate baseDate,
        Integer horizonDays,
        LocalDate endDate,
        ForecastStatus status,
        Long currentBalance,
        Long availableToday,
        Long minimumBalance,
        LocalDate riskDate,
        String calculationVersion,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {

    public static CashflowForecastResponse from(CashflowForecast forecast) {
        return new CashflowForecastResponse(
                forecast.getForecastId(),
                forecast.getBaseDate(),
                forecast.getHorizonDays(),
                forecast.getBaseDate().plusDays(forecast.getHorizonDays() - 1L),
                forecast.getStatus(),
                forecast.getCurrentBalance(),
                forecast.getAvailableToday(),
                forecast.getMinimumBalance(),
                forecast.getRiskDate(),
                forecast.getCalculationVersion(),
                forecast.getCreatedAt(),
                forecast.getCompletedAt()
        );
    }
}
