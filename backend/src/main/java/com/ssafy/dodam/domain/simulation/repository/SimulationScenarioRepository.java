package com.ssafy.dodam.domain.simulation.repository;

import com.ssafy.dodam.domain.simulation.entity.SimulationScenario;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationScenarioRepository extends JpaRepository<SimulationScenario, Long> {
    List<SimulationScenario> findAllBySimulationIdOrderBySequenceNoAsc(Long simulationId);
}
