package com.ssafy.dodam.domain.cashflow.dto.response;

import com.ssafy.dodam.domain.cashflow.entity.CashflowRisk;
import com.ssafy.dodam.domain.cashflow.entity.RiskSeverity;
import java.time.LocalDate;

/**
 * 위험 1건 응답.
 *
 * <p>`expectedBalance`는 그 출금 <b>직전</b>의 예상잔액이다. 하루 마감 잔액이 아니므로
 * `daily-balances`의 같은 날짜 값과 다를 수 있다.
 */
public record CashflowRiskResponse(
        Long riskId,
        LocalDate riskDate,
        String type,
        RiskSeverity severity,
        String message,
        Long expectedBalance
) {

    public static CashflowRiskResponse from(CashflowRisk risk) {
        return new CashflowRiskResponse(
                risk.getRiskId(),
                risk.getRiskDate(),
                risk.getType(),
                risk.getSeverity(),
                risk.getMessage(),
                risk.getExpectedBalance()
        );
    }
}
