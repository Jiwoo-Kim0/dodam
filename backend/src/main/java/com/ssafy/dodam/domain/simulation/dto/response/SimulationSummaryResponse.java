package com.ssafy.dodam.domain.simulation.dto.response;

import com.ssafy.dodam.domain.simulation.entity.Simulation;
import com.ssafy.dodam.domain.simulation.entity.SimulationStatus;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import java.time.LocalDateTime;

/** GET /api/v1/simulations 목록 항목. 스냅샷은 싣지 않는다. */
public record SimulationSummaryResponse(
        Long simulationId,
        SimulationType type,
        String title,
        String summary,
        SimulationStatus status,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
    public static SimulationSummaryResponse from(Simulation simulation) {
        return new SimulationSummaryResponse(simulation.getSimulationId(), simulation.getType(), simulation.getTitle(),
                simulation.getSummary(), simulation.getStatus(), simulation.getCreatedAt(), simulation.getCompletedAt());
    }
}
