package com.ssafy.dodam.domain.cashflow.dto.request;

import com.ssafy.dodam.domain.cashflow.service.CashflowForecastPeriodPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

/**
 * 현금흐름 예측 실행 요청. 세 값 모두 선택이며 본문 자체를 생략할 수 있다.
 *
 * <p>기준일 규칙과 기본값은 {@link CashflowForecastPeriodPolicy}가 판단한다. 여기서는 형식 범위만
 * 검증해 잘못된 `horizonDays`를 필드 단위 오류로 돌려준다. 상한은 정책 상수를 그대로 참조해 값을
 * 두 곳에 적지 않는다.
 */
public record CashflowForecastRequest(
        LocalDate baseDate,
        @Min(1) @Max(CashflowForecastPeriodPolicy.MAX_HORIZON_DAYS) Integer horizonDays,
        Boolean forceRefresh
) {

    /** 본문이 없으면 모든 값이 기본값인 요청으로 본다. */
    public static CashflowForecastRequest emptyIfNull(CashflowForecastRequest request) {
        return request == null ? new CashflowForecastRequest(null, null, null) : request;
    }

    public boolean forceRefreshOrFalse() {
        return Boolean.TRUE.equals(forceRefresh);
    }
}
