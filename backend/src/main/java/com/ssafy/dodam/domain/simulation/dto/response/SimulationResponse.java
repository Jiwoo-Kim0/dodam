package com.ssafy.dodam.domain.simulation.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.simulation.entity.Simulation;
import com.ssafy.dodam.domain.simulation.entity.SimulationScenario;
import com.ssafy.dodam.domain.simulation.entity.SimulationGoalImpact;
import com.ssafy.dodam.domain.simulation.entity.SimulationStatus;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 시뮬레이션 상세. 스냅샷 JSON은 저장값 그대로다(재계산 없음).
 *
 * @param request  요청 파라미터. 저장 스냅샷에서 `requestHash`를 뺀 것
 * @param before   기준선. `coverage`(입력 누락 사유) 포함
 * @param after    적용 후. `assumptions`(적용 가정) 포함
 */
public record SimulationResponse(
        Long simulationId,
        SimulationType type,
        String title,
        String summary,
        SimulationStatus status,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        JsonNode request,
        List<SimulationScenarioResponse> scenarios,
        JsonNode before,
        JsonNode after,
        JsonNode comparison,
        JsonNode cashflow,
        List<SimulationGoalImpactResponse> goalImpacts
) {
    public static final String REQUEST_HASH_FIELD = "requestHash";

    public static SimulationResponse of(Simulation simulation, List<SimulationScenario> scenarios,
            List<SimulationGoalImpact> impacts) {
        JsonNode request = simulation.getRequestSnapshot();
        if (request != null && request.isObject()) {
            request = request.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) request).remove(REQUEST_HASH_FIELD);
        }
        return new SimulationResponse(simulation.getSimulationId(), simulation.getType(), simulation.getTitle(),
                simulation.getSummary(), simulation.getStatus(), simulation.getCreatedAt(), simulation.getCompletedAt(),
                request, scenarios.stream().map(SimulationScenarioResponse::from).toList(),
                simulation.getBeforeSnapshot(), simulation.getAfterSnapshot(), simulation.getComparison(),
                simulation.getCashflowImpact(), impacts.stream().map(SimulationGoalImpactResponse::from).toList());
    }
}
