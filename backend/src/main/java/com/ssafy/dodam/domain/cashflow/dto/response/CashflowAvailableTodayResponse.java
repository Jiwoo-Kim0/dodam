package com.ssafy.dodam.domain.cashflow.dto.response;

/**
 * 오늘 사용가능금액 응답.
 *
 * <p>`availableToday`는 예측 헤더에도 들어 있지만 최상위에 한 번 더 싣는다. 이 엔드포인트의 결과가
 * 그 값 하나이므로 호출자가 헤더 안을 파고들지 않게 한다.
 *
 * <p><b>null인 경우</b>: 계산 정의가 확정되기 전(S15P21D105-540 시점)에 완료된 예측에는 값이 없다.
 * 0원으로 채우지 않는다. "쓸 돈이 없다"와 "계산된 적이 없다"는 다르다. 예측을 다시 실행하면 채워진다.
 */
public record CashflowAvailableTodayResponse(
        CashflowForecastResponse forecast,
        Long availableToday
) {

    public static CashflowAvailableTodayResponse from(CashflowForecastResponse forecast) {
        return new CashflowAvailableTodayResponse(forecast, forecast.availableToday());
    }
}
