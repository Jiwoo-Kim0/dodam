package com.ssafy.dodam.domain.cashflow.dto;

import com.ssafy.dodam.domain.cashflow.entity.CashflowForecast;
import java.util.List;

/**
 * 예측 실행 결과.
 *
 * @param cached 재계산 없이 기존 예측을 재사용했는지 여부
 * @param reasons 수집 단계에서 누락 가능성이 있던 원본의 사유. 저장 컬럼이 없어 이 응답에서만 전달되며
 *                캐시를 재사용한 경우에는 복원할 수 없어 비어 있다
 */
public record CashflowForecastResult(CashflowForecast forecast, boolean cached, List<String> reasons) {
    public CashflowForecastResult {
        reasons = List.copyOf(reasons);
    }
}
