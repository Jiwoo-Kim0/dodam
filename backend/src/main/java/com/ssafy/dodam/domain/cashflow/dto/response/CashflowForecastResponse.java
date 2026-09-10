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
 * <p>`requestHash`는 캐시·멱등 판정용 내부 지문이므로 응답에 넣지 않는다. `availableToday`는 계산
 * 정의가 S15P21D105-542에서 확정되므로 그때 추가한다.
 */
public record CashflowForecastResponse(
        Long forecastId,
        LocalDate baseDate,
        Integer horizonDays,
        LocalDate endDate,
        ForecastStatus status,
        Long currentBalance,
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
                forecast.getMinimumBalance(),
                forecast.getRiskDate(),
                forecast.getCalculationVersion(),
                forecast.getCreatedAt(),
                forecast.getCompletedAt()
        );
    }
}
