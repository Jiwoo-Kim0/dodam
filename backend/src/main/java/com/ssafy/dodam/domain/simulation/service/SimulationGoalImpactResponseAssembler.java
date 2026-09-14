package com.ssafy.dodam.domain.simulation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.simulation.dto.response.SimulationGoalImpactResponse;
import com.ssafy.dodam.domain.simulation.entity.Simulation;
import com.ssafy.dodam.domain.simulation.entity.SimulationGoalImpact;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 목표 영향 응답의 단일 조립 지점. 엔티티(`simulation_goal_impact`)에는 날짜만 있고 계산 상태·사유는
 * `after_snapshot.goalImpactDetails`에 있어 goalId로 결합한다. 실행 POST·상세 GET·goal-impact GET이 모두 여기를 거치므로
 * 세 경로의 목표 배열이 정렬(goalId 오름차순)까지 같다. 확장 필드가 없는 과거 결과는 status·reasons 없이 기존 모양 그대로다.
 */
public final class SimulationGoalImpactResponseAssembler {
    private SimulationGoalImpactResponseAssembler() {
    }

    public static List<SimulationGoalImpactResponse> assemble(Simulation simulation, List<SimulationGoalImpact> impacts) {
        Map<Long, JsonNode> details = new HashMap<>();
        JsonNode after = simulation.getAfterSnapshot();
        if (after != null && after.path(SimulationStore.GOAL_IMPACT_DETAILS_FIELD).isArray()) {
            for (JsonNode detail : after.get(SimulationStore.GOAL_IMPACT_DETAILS_FIELD)) {
                if (detail.hasNonNull("goalId")) {
                    details.put(detail.get("goalId").asLong(), detail);
                }
            }
        }
        List<SimulationGoalImpactResponse> responses = new ArrayList<>();
        for (SimulationGoalImpact impact : impacts) {
            SimulationGoalImpactResponse response = SimulationGoalImpactResponse.from(impact);
            JsonNode detail = details.get(impact.getGoalId());
            if (detail != null) {
                List<String> reasons = new ArrayList<>();
                detail.path("reasons").forEach(reason -> reasons.add(reason.asText()));
                response = response.withDetail(detail.path("status").asText(null), reasons);
            }
            responses.add(response);
        }
        responses.sort(Comparator.comparing(SimulationGoalImpactResponse::goalId));
        return responses;
    }
}
