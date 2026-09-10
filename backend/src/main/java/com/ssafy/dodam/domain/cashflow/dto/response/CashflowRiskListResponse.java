package com.ssafy.dodam.domain.cashflow.dto.response;

import com.ssafy.dodam.domain.cashflow.entity.RiskSeverity;
import java.util.List;

/**
 * 위험 목록 응답.
 *
 * <p>어느 예측의 위험인지 알아야 잔액·이벤트 조회와 대조할 수 있어 예측 헤더를 함께 싣는다.
 * 적용한 필터도 되돌려 준다. 빈 목록이 "위험이 없다"인지 "필터에 걸린 게 없다"인지 구분하기 위해서다.
 */
public record CashflowRiskListResponse(
        CashflowForecastResponse forecast,
        String type,
        RiskSeverity severity,
        List<CashflowRiskResponse> risks
) {
    public CashflowRiskListResponse {
        risks = List.copyOf(risks);
    }
}
