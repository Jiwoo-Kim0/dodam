package com.ssafy.dodam.domain.simulation.dto;

import java.util.Objects;

/**
 * 계산기 출력 한 벌. {@code result}는 저장·응답용, {@code ledger}는 복합 합성용 내부 자료다.
 * 단독 실행은 result만 저장하고 ledger는 버린다.
 */
public record ScenarioComputation(SimulationResult result, ScenarioLedger ledger) {
    public ScenarioComputation {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(ledger, "ledger");
    }
}
