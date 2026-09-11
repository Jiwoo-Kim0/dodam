package com.ssafy.dodam.domain.cashflow.dto;

import com.ssafy.dodam.domain.cashflow.entity.CashflowRisk;
import java.util.List;

/**
 * 위험 탐지 결과.
 *
 * @param risks 저장할 위험 행. 발생 순서를 지킨다
 * @param availableToday 오늘 사용가능금액. 계산 정의는 {@code CashflowRiskDetector} 참고
 */
public record CashflowRiskAssessment(List<CashflowRisk> risks, long availableToday) {
    public CashflowRiskAssessment {
        risks = List.copyOf(risks);
    }
}
