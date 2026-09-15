package com.ssafy.dodam.domain.simulation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.simulation.dto.SimulationInput;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult;
import com.ssafy.dodam.domain.simulation.dto.response.SimulationResponse;
import com.ssafy.dodam.domain.simulation.entity.Simulation;
import com.ssafy.dodam.domain.simulation.entity.SimulationGoalImpact;
import com.ssafy.dodam.domain.simulation.entity.SimulationScenario;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import com.ssafy.dodam.domain.simulation.repository.SimulationGoalImpactRepository;
import com.ssafy.dodam.domain.simulation.repository.SimulationRepository;
import com.ssafy.dodam.domain.simulation.repository.SimulationScenarioRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결과를 `simulation` + `simulation_scenario`(단독 1행, 복합은 자식 N행) + `simulation_goal_impact`로 한 트랜잭션에 저장한다.
 * Runner의 잠금 트랜잭션에 참여한다(MANDATORY) — 잠금 없이 저장하는 경로를 만들지 않기 위해서다.
 */
@Component
public class SimulationStore {
    static final String CALCULATION_VERSION_FIELD = "calculationVersion";
    static final String COVERAGE_FIELD = "coverage";
    static final String ASSUMPTIONS_FIELD = "assumptions";
    /** 585 유형의 확장 필드. null이면 쓰지 않아 기존 spending/saving 저장 모양이 바뀌지 않는다. */
    public static final String DETAILS_FIELD = "details";
    public static final String GOAL_IMPACT_DETAILS_FIELD = "goalImpactDetails";
    private final SimulationRepository simulations;
    private final SimulationScenarioRepository scenarios;
    private final SimulationGoalImpactRepository goalImpacts;
    private final ObjectMapper json;

    public SimulationStore(SimulationRepository simulations, SimulationScenarioRepository scenarios,
            SimulationGoalImpactRepository goalImpacts, ObjectMapper json) {
        this.simulations = simulations;
        this.scenarios = scenarios;
        this.goalImpacts = goalImpacts;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Simulation save(Long userId, SimulationInput input, String requestHash, SimulationBaseline baseline,
            SimulationResult result, LocalDateTime now) {
        ObjectNode request = input.parameters(json);
        request.put(SimulationResponse.REQUEST_HASH_FIELD, requestHash);
        request.put(CALCULATION_VERSION_FIELD, SimulationCalculationVersion.CURRENT);
        ObjectNode before = json.valueToTree(result.before());
        before.set(COVERAGE_FIELD, json.valueToTree(baseline.coverage()));
        ObjectNode after = json.valueToTree(result.after());
        after.set(ASSUMPTIONS_FIELD, json.valueToTree(result.assumptions()));
        if (result.details() != null) {
            after.set(DETAILS_FIELD, json.valueToTree(result.details()));
        }
        if (result.goalImpactDetails() != null) {
            after.set(GOAL_IMPACT_DETAILS_FIELD, json.valueToTree(result.goalImpactDetails()));
        }
        JsonNode comparison = json.valueToTree(result.comparison());
        JsonNode cashflow = json.valueToTree(result.cashflow());

        Simulation saved = simulations.save(Simulation.completed(userId, input.type(), result.title(), result.summary(),
                request, before, after, comparison, cashflow, now));
        // 단독은 자기 자신 1행, 복합은 자식 N행(입력 순서 = sequenceNo). 부모 파라미터는 request_snapshot에만 있다
        List<SimulationInput> scenarioInputs = input.scenarios();
        for (int i = 0; i < scenarioInputs.size(); i++) {
            SimulationInput scenario = scenarioInputs.get(i);
            scenarios.save(SimulationScenario.of(saved.getSimulationId(), i + 1, scenario.type(), scenario.parameters(json)));
        }
        for (SimulationResult.GoalImpact impact : result.goalImpacts()) {
            goalImpacts.save(SimulationGoalImpact.of(userId, saved.getSimulationId(), impact.goalId(), impact.before(), impact.after()));
        }
        return saved;
    }

    /**
     * 같은 날 같은 지문의 기존 결과. 지문은 JSON 안에 있어 하루치 후보를 가져와 비교한다.
     * 기준일이 다르면 기준선이 달라지므로 재사용하지 않는다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Simulation> findReusable(Long userId, SimulationType type, String requestHash, LocalDate day) {
        return simulations
                .findAllByUserIdAndTypeAndDeletedAtIsNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderBySimulationIdDesc(
                        userId, type, day.atStartOfDay(), day.plusDays(1).atStartOfDay())
                .stream()
                .filter(s -> s.getRequestSnapshot() != null
                        && requestHash.equals(s.getRequestSnapshot().path(SimulationResponse.REQUEST_HASH_FIELD).asText(null)))
                .findFirst();
    }
}
