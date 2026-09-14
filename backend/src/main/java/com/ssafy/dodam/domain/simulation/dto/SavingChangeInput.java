package com.ssafy.dodam.domain.simulation.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;

/**
 * 저축액 변경 입력.
 *
 * @param accountId          본인 소유로 확인된 계좌 id. null이면 전체 모으기 목표. 계좌번호 원문·해시는 여기까지 오지 않는다
 * @param monthlySavingAmount 새 월 저축액(원, 0 이상, 절대값). 현재 속도와의 차이는 계산기가 구한다
 */
public record SavingChangeInput(Long accountId, long monthlySavingAmount, int durationMonths) implements SimulationInput {
    public SavingChangeInput {
        if (monthlySavingAmount < 0) {
            throw new IllegalArgumentException("monthlySavingAmount는 0 이상이어야 합니다.");
        }
        durationMonths = SimulationInput.durationOrDefault(durationMonths);
    }

    @Override
    public SimulationType type() {
        return SimulationType.SAVING;
    }

    @Override
    public String fingerprint() {
        return (accountId == null ? "" : accountId.toString()) + "|" + monthlySavingAmount + "|" + durationMonths;
    }

    @Override
    public ObjectNode parameters(ObjectMapper json) {
        ObjectNode node = json.createObjectNode();
        node.put("accountId", accountId);
        node.put("monthlySavingAmount", monthlySavingAmount);
        node.put("durationMonths", durationMonths);
        return node;
    }
}
