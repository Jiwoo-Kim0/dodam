package com.ssafy.dodam.domain.cashflow.dto.response;

import com.ssafy.dodam.domain.cashflow.dto.CashflowForecastResult;
import java.util.List;

/**
 * 예측 실행 응답.
 *
 * @param cached true면 재계산 없이 기존 예측을 재사용했다. 진행 중인 같은 요청이 있을 때도
 *               true이며, 이 경우 `forecast.status`는 아직 `PENDING`이나 `RUNNING`이다
 * @param incompleteReasons 수집 단계에서 누락 가능성이 있던 원본의 사유. 저장 컬럼이 없어 캐시를
 *                          재사용한 경우에는 복원할 수 없어 비어 있다(S15P21D105-540)
 */
public record CashflowForecastRunResponse(
        CashflowForecastResponse forecast,
        boolean cached,
        List<String> incompleteReasons
) {

    public static CashflowForecastRunResponse from(CashflowForecastResult result) {
        return new CashflowForecastRunResponse(
                CashflowForecastResponse.from(result.forecast()),
                result.cached(),
                result.reasons()
        );
    }
}
