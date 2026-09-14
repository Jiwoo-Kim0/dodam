package com.ssafy.dodam.domain.simulation.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 시뮬레이션을 구성하는 시나리오 1건. 단일 유형 시뮬레이션은 `sequence_no = 1` 한 건이고,
 * 복합 시뮬레이션(COMBINED)이 여러 건을 갖는다. `parameters`에는 요청 파라미터만 넣고 계좌번호 원문은 제외한다.
 */
@Getter
@Entity
@Table(name = "simulation_scenario", uniqueConstraints = @UniqueConstraint(
        name = "uk_simulation_scenario_sequence", columnNames = {"simulation_id", "sequence_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimulationScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scenario_id")
    private Long scenarioId;

    @Column(name = "simulation_id", nullable = false)
    private Long simulationId;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "scenario_type", nullable = false, length = 50)
    private String scenarioType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "JSON", nullable = false)
    private JsonNode parameters;

    private SimulationScenario(Long simulationId, int sequenceNo, String scenarioType, JsonNode parameters) {
        if (sequenceNo <= 0) {
            throw new IllegalArgumentException("sequenceNo는 양수여야 합니다.");
        }
        this.simulationId = Objects.requireNonNull(simulationId, "simulationId");
        this.sequenceNo = sequenceNo;
        this.scenarioType = Objects.requireNonNull(scenarioType, "scenarioType");
        this.parameters = Objects.requireNonNull(parameters, "parameters");
    }

    public static SimulationScenario of(Long simulationId, int sequenceNo, SimulationType scenarioType, JsonNode parameters) {
        return new SimulationScenario(simulationId, sequenceNo, scenarioType.name(), parameters);
    }
}
