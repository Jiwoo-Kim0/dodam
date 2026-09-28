package com.ssafy.dodam.domain.simulation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ssafy.dodam.domain.simulation.entity.SimulationGoalImpact;
import java.time.LocalDate;
import java.util.List;

/**
 * @param differenceDays {@code after − before}. 음수면 앞당겨짐. 한쪽이 null(달성 불가)이면 null
 * @param status  585 유형의 계산 상태({@code COMPUTED}/{@code NOT_COMPUTABLE}). 저장 JSON에 없는 과거 결과·spending/saving은 생략된다
 * @param reasons 계산 불가·가정 사유 코드. status와 함께만 나온다
 */
public record SimulationGoalImpactResponse(Long goalId, LocalDate beforeForecastDate, LocalDate afterForecastDate,
        Integer differenceDays,
        @JsonInclude(JsonInclude.Include.NON_NULL) String status,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> reasons) {
    public static SimulationGoalImpactResponse from(SimulationGoalImpact impact) {
        return new SimulationGoalImpactResponse(impact.getGoalId(), impact.getBeforeForecastDate(),
                impact.getAfterForecastDate(), impact.getDifferenceDays(), null, null);
    }

    public SimulationGoalImpactResponse withDetail(String status, List<String> reasons) {
        return new SimulationGoalImpactResponse(goalId, beforeForecastDate, afterForecastDate, differenceDays, status,
                reasons == null ? null : List.copyOf(reasons));
    }
}
