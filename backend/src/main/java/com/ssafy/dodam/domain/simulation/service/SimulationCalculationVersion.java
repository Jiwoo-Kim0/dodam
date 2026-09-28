package com.ssafy.dodam.domain.simulation.service;

/**
 * 계산 규칙 버전. 요청 지문에 들어가므로 계산식·기준선 정의가 바뀌면 값을 올려야 같은 요청이 옛 결과를 재사용하지 않는다.
 * 저장된 행의 버전은 `request_snapshot.calculationVersion`에 남는다.
 */
public final class SimulationCalculationVersion {
    /**
     * v2: 585에서 결과 JSON에 details·goalImpactDetails·cashflow 확장 필드가 추가됐다. v1 결과는 재사용 대상이 아니다.
     * v3: 586에서 카드 3종·복합이 추가되고 계산기가 내부 납부 자료를 함께 만든다. v2 결과는 재사용 대상이 아니다.
     */
    public static final String CURRENT = "v3-card-combined";

    private SimulationCalculationVersion() {
    }
}
