package com.ssafy.dodam.domain.cashflow.entity;

/**
 * 현금흐름 예측 실행 상태.
 *
 * <p>값은 `chk_cashflow_forecast_status`와 ERD 주요 코드값 표를 그대로 따른다. 코드를 추가하려면
 * DDL·ERD·API 명세를 함께 고쳐야 한다.
 */
public enum ForecastStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
