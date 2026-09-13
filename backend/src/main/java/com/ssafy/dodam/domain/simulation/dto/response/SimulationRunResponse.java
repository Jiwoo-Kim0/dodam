package com.ssafy.dodam.domain.simulation.dto.response;

/**
 * 실행 응답. {@code created=false}면 같은 날 같은 요청 지문의 기존 결과를 돌려준 것이다(HTTP 200, 신규는 201).
 */
public record SimulationRunResponse(SimulationResponse simulation, boolean created) {
}
