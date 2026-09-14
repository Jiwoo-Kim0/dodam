package com.ssafy.dodam.domain.simulation.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.simulation.entity.SimulationScenario;

public record SimulationScenarioResponse(int sequenceNo, String scenarioType, JsonNode parameters) {
    public static SimulationScenarioResponse from(SimulationScenario scenario) {
        return new SimulationScenarioResponse(scenario.getSequenceNo(), scenario.getScenarioType(), scenario.getParameters());
    }
}
