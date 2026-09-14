package com.ssafy.dodam.domain.simulation.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;

/**
 * 소비 감소 입력.
 *
 * @param categoryCode    줄일 카테고리 코드. null이면 전체 지출. 존재 검증은 끝난 값이어야 한다
 * @param monthlyReduction 월 감소액(원, 양수). 기준선 평균 지출 이하 검증은 계산기가 한다(기준선이 있어야 알 수 있다)
 */
public record SpendingReductionInput(String categoryCode, long monthlyReduction, int durationMonths) implements SimulationInput {
    public SpendingReductionInput {
        if (monthlyReduction <= 0) {
            throw new IllegalArgumentException("monthlyReduction은 양수여야 합니다.");
        }
        durationMonths = SimulationInput.durationOrDefault(durationMonths);
    }

    @Override
    public SimulationType type() {
        return SimulationType.SPENDING;
    }

    @Override
    public String fingerprint() {
        return (categoryCode == null ? "" : categoryCode) + "|" + monthlyReduction + "|" + durationMonths;
    }

    @Override
    public ObjectNode parameters(ObjectMapper json) {
        ObjectNode node = json.createObjectNode();
        node.put("category", categoryCode);
        node.put("monthlyReduction", monthlyReduction);
        node.put("durationMonths", durationMonths);
        return node;
    }
}
