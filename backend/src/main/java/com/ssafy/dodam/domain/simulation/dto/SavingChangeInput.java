package com.ssafy.dodam.domain.simulation.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;

/**
 * 저축액 변경 입력.
 *
 * @param accountId          본인 소유로 확인된 계좌 id. 계좌번호 원문·해시는 여기까지 오지 않는다
 * @param goalId             본인 소유·ACTIVE·SAVING으로 확인된 목표 id(619). {@code accountId}와 함께 줄 수 없다.
 *                           둘 다 null이면 전체 모으기 목표
 * @param monthlySavingAmount 새 월 저축액(원, 0 이상, 절대값). 현재 속도와의 차이는 계산기가 구한다
 */
public record SavingChangeInput(Long accountId, Long goalId, long monthlySavingAmount, int durationMonths) implements SimulationInput {
    public SavingChangeInput {
        if (accountId != null && goalId != null) {
            throw new IllegalArgumentException("accountId와 goalId는 함께 지정할 수 없습니다.");
        }
        if (monthlySavingAmount < 0) {
            throw new IllegalArgumentException("monthlySavingAmount는 0 이상이어야 합니다.");
        }
        durationMonths = SimulationInput.durationOrDefault(durationMonths);
    }

    public SavingChangeInput(Long accountId, long monthlySavingAmount, int durationMonths) {
        this(accountId, null, monthlySavingAmount, durationMonths);
    }

    @Override
    public SimulationType type() {
        return SimulationType.SAVING;
    }

    /**
     * goalId가 없으면 619 이전과 같은 문자열이어야 한다 — 배포 전에 저장된 같은 날 결과가 계속 재사용되도록.
     * goalId가 있을 때만 접미사를 붙인다.
     */
    @Override
    public String fingerprint() {
        return (accountId == null ? "" : accountId.toString()) + "|" + monthlySavingAmount + "|" + durationMonths
                + (goalId == null ? "" : "|g=" + goalId);
    }

    @Override
    public ObjectNode parameters(ObjectMapper json) {
        ObjectNode node = json.createObjectNode();
        node.put("accountId", accountId);
        node.put("goalId", goalId);
        node.put("monthlySavingAmount", monthlySavingAmount);
        node.put("durationMonths", durationMonths);
        return node;
    }
}
