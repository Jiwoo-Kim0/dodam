package com.ssafy.dodam.domain.simulation.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * POST /api/v1/simulations/combined 본문. 명세 17: {@code scenarios[]}.
 * 각 원소는 {@code { "type": <COMBINED 제외 9종>, "parameters": <해당 단독 API 본문> }}이며 그 외 필드(simulationId, 다른 자식
 * 참조 등)는 400이다. 원소를 {@link JsonNode}로 받는 이유는 type별로 다른 DTO를 허용 목록으로 파싱하고 알 수 없는 필드를
 * 전역 설정 변경 없이 거부하기 위해서다({@code CombinedScenarioParser}).
 */
public record CombinedSimulationRequest(
        @NotNull @Size(min = 2, max = 10) List<@NotNull JsonNode> scenarios
) {
}
