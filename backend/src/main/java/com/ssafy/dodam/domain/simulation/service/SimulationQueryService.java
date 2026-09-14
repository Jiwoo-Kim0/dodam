package com.ssafy.dodam.domain.simulation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.simulation.dto.response.SimulationGoalImpactResponse;
import com.ssafy.dodam.domain.simulation.dto.response.SimulationResponse;
import com.ssafy.dodam.domain.simulation.dto.response.SimulationSummaryResponse;
import com.ssafy.dodam.domain.simulation.entity.Simulation;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import com.ssafy.dodam.domain.simulation.repository.SimulationGoalImpactRepository;
import com.ssafy.dodam.domain.simulation.repository.SimulationRepository;
import com.ssafy.dodam.domain.simulation.repository.SimulationScenarioRepository;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.response.PageResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장된 시뮬레이션을 그대로 돌려준다. 현재 데이터로 재계산하지 않는다.
 * 타인·없는·삭제된 시뮬레이션은 모두 {@code RESOURCE_NOT_FOUND}다.
 */
@Service
@Transactional(readOnly = true)
public class SimulationQueryService {
    static final String NOT_FOUND_MESSAGE = "시뮬레이션을 찾을 수 없습니다.";
    private final SimulationRepository simulations;
    private final SimulationScenarioRepository scenarios;
    private final SimulationGoalImpactRepository goalImpacts;
    private final SimulationClock clock;

    public SimulationQueryService(SimulationRepository simulations, SimulationScenarioRepository scenarios,
            SimulationGoalImpactRepository goalImpacts, SimulationClock clock) {
        this.simulations = simulations;
        this.scenarios = scenarios;
        this.goalImpacts = goalImpacts;
        this.clock = clock;
    }

    public PageResponse<SimulationSummaryResponse> list(Long userId, SimulationType type, int page, int size) {
        return PageResponse.from(simulations.findOwned(userId, type, PageRequest.of(page, size)), SimulationSummaryResponse::from);
    }

    public SimulationResponse detail(Long userId, Long simulationId) {
        return toResponse(userId, owned(userId, simulationId));
    }

    public SimulationResponse toResponse(Long userId, Simulation simulation) {
        return SimulationResponse.of(simulation, scenarios.findAllBySimulationIdOrderBySequenceNoAsc(simulation.getSimulationId()),
                goalImpacts.findAllByUserIdAndSimulationIdOrderByGoalIdAsc(userId, simulation.getSimulationId()));
    }

    public JsonNode comparison(Long userId, Long simulationId) {
        return owned(userId, simulationId).getComparison();
    }

    public JsonNode cashflow(Long userId, Long simulationId) {
        return owned(userId, simulationId).getCashflowImpact();
    }

    public List<SimulationGoalImpactResponse> goalImpact(Long userId, Long simulationId) {
        owned(userId, simulationId);
        return goalImpacts.findAllByUserIdAndSimulationIdOrderByGoalIdAsc(userId, simulationId).stream()
                .map(SimulationGoalImpactResponse::from).toList();
    }

    /**
     * 자산·부채·금융비용 변화. `comparison.<section>` 배열을 메모리에서 페이지로 자른다.
     * 항목 수가 수십 건을 넘지 않는 저장 JSON이라 별도 테이블·쿼리를 두지 않았다.
     */
    public PageResponse<JsonNode> comparisonSection(Long userId, Long simulationId, String section, int page, int size) {
        JsonNode comparison = owned(userId, simulationId).getComparison();
        List<JsonNode> items = new ArrayList<>();
        if (comparison != null && comparison.path(section).isArray()) {
            comparison.get(section).forEach(items::add);
        }
        int from = Math.min(page * size, items.size());
        int to = Math.min(from + size, items.size());
        return PageResponse.from(new PageImpl<>(items.subList(from, to), PageRequest.of(page, size), items.size()), item -> item);
    }

    @Transactional
    public void delete(Long userId, Long simulationId) {
        owned(userId, simulationId).delete(clock.now());
    }

    private Simulation owned(Long userId, Long simulationId) {
        return simulations.findBySimulationIdAndUserIdAndDeletedAtIsNull(simulationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }
}
