package com.ssafy.dodam.domain.simulation.service;

/**
 * 계산 규칙 버전. 요청 지문에 들어가므로 계산식·기준선 정의가 바뀌면 값을 올려야 같은 요청이 옛 결과를 재사용하지 않는다.
 * 저장된 행의 버전은 `request_snapshot.calculationVersion`에 남는다.
 */
public final class SimulationCalculationVersion {
    public static final String CURRENT = "v1-spending-saving";

    private SimulationCalculationVersion() {
    }
}
