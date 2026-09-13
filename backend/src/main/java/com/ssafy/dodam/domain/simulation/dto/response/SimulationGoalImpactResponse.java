package com.ssafy.dodam.domain.simulation.dto.response;

import com.ssafy.dodam.domain.simulation.entity.SimulationGoalImpact;
import java.time.LocalDate;

/**
 * @param differenceDays {@code after − before}. 음수면 앞당겨짐. 한쪽이 null(달성 불가)이면 null
 */
public record SimulationGoalImpactResponse(Long goalId, LocalDate beforeForecastDate, LocalDate afterForecastDate,
        Integer differenceDays) {
    public static SimulationGoalImpactResponse from(SimulationGoalImpact impact) {
        return new SimulationGoalImpactResponse(impact.getGoalId(), impact.getBeforeForecastDate(),
                impact.getAfterForecastDate(), impact.getDifferenceDays());
    }
}
