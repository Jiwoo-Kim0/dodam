package com.ssafy.dodam.domain.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.domain.simulation.dto.SimulationInput;
import com.ssafy.dodam.domain.simulation.entity.Simulation;
import com.ssafy.dodam.domain.simulation.entity.SimulationGoalImpact;
import com.ssafy.dodam.domain.simulation.entity.SimulationScenario;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import com.ssafy.dodam.domain.simulation.service.GoalAchievementProjection;
import com.ssafy.dodam.domain.simulation.service.SimulationRequestHash;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** DB 없이 달성일 식·요청 지문·엔티티 검증만 본다. */
class SimulationModelTest {
    private static final LocalDate BASE = LocalDate.of(2026, 9, 13);
    private static final LocalDateTime NOW = BASE.atTime(12, 0);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void projectionMatchesGoalForecastRules() {
        assertThat(GoalAchievementProjection.expectedDate(0, 0, BASE)).isEqualTo(BASE);
        assertThat(GoalAchievementProjection.expectedDate(-5, 100, BASE)).isEqualTo(BASE);
        assertThat(GoalAchievementProjection.expectedDate(100, 0, BASE)).isNull();
        assertThat(GoalAchievementProjection.expectedDate(1_000_000, 100_000, BASE)).isEqualTo(BASE.plusMonths(10));
        assertThat(GoalAchievementProjection.expectedDate(1_000_001, 100_000, BASE)).isEqualTo(BASE.plusMonths(11));
        assertThat(SimulationGoalImpact.differenceDays(BASE, BASE.minusDays(3))).isEqualTo(-3);
        assertThat(SimulationGoalImpact.differenceDays(null, BASE)).isNull();
    }

    @Test
    void requestHashIsStableAndSensitiveToEveryInput() {
        String a = SimulationRequestHash.of(1L, SimulationType.SPENDING, "FOOD|10|12", BASE, "v1");
        assertThat(a).hasSize(64)
                .isEqualTo(SimulationRequestHash.of(1L, SimulationType.SPENDING, "FOOD|10|12", BASE, "v1"))
                .isNotEqualTo(SimulationRequestHash.of(2L, SimulationType.SPENDING, "FOOD|10|12", BASE, "v1"))
                .isNotEqualTo(SimulationRequestHash.of(1L, SimulationType.SAVING, "FOOD|10|12", BASE, "v1"))
                .isNotEqualTo(SimulationRequestHash.of(1L, SimulationType.SPENDING, "FOOD|10|12", BASE.plusDays(1), "v1"))
                .isNotEqualTo(SimulationRequestHash.of(1L, SimulationType.SPENDING, "FOOD|10|12", BASE, "v2"))
                .isNotEqualTo(SimulationRequestHash.of(1L, SimulationType.SPENDING, "FOOD|1|012", BASE, "v1"));
    }

    @Test
    void entitiesRejectInvalidValuesBeforeTheDatabase() {
        assertThatThrownBy(() -> SimulationScenario.of(1L, 0, SimulationType.SPENDING, json.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Simulation.completed(1L, SimulationType.SPENDING, "t".repeat(256), null, null,
                json.createObjectNode(), json.createObjectNode(), null, null, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Simulation.completed(1L, SimulationType.SPENDING, null, null, null, null,
                json.createObjectNode(), null, null, NOW)).isInstanceOf(NullPointerException.class);
        Simulation simulation = Simulation.completed(1L, SimulationType.SAVING, "t", "s", null, json.createObjectNode(),
                json.createObjectNode(), null, null, NOW);
        assertThat(simulation.getStatus().name()).isEqualTo("COMPLETED");
        assertThat(simulation.getCompletedAt()).isEqualTo(NOW);
        simulation.delete(NOW.plusDays(1));
        simulation.delete(NOW.plusDays(2));
        assertThat(simulation.getDeletedAt()).isEqualTo(NOW.plusDays(1));
        assertThatThrownBy(() -> SimulationInput.durationOrDefault(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SimulationInput.durationOrDefault(121)).isInstanceOf(IllegalArgumentException.class);
        assertThat(SimulationInput.durationOrDefault(null)).isEqualTo(12);
    }
}
