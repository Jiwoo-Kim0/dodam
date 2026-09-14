package com.ssafy.dodam.domain.simulation.repository;

import com.ssafy.dodam.domain.simulation.entity.SimulationGoalImpact;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 소유권은 `userId` 조건으로 건다. 헤더(`simulation`)의 소유 확인을 거친 뒤 호출하더라도 조건을 빼지 않는다. */
public interface SimulationGoalImpactRepository extends JpaRepository<SimulationGoalImpact, Long> {
    List<SimulationGoalImpact> findAllByUserIdAndSimulationIdOrderByGoalIdAsc(Long userId, Long simulationId);
}
