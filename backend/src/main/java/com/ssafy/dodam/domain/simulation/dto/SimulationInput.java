package com.ssafy.dodam.domain.simulation.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;

/**
 * 계산기에 넘기는 확정된 입력. HTTP 요청과 분리한 이유는 두 가지다.
 * 계좌번호·카테고리 문자열 같은 외부 식별자를 내부 id·코드로 바꾸는 검증이 계산 전에 끝나야 하고,
 * 요청 지문과 저장 파라미터에 민감번호가 섞이지 않아야 한다.
 */
public interface SimulationInput {
    SimulationType type();

    /** 기준일 다음 달부터 몇 개월을 볼지. 1~120, 기본 12 */
    int durationMonths();

    /** 요청 지문 입력. 같은 값이면 같은 계산이다. 민감번호 대신 내부 id를 쓴다. */
    String fingerprint();

    /** `simulation_scenario.parameters`·`request_snapshot`에 저장할 파라미터. 민감번호 제외 */
    ObjectNode parameters(ObjectMapper json);

    int DEFAULT_DURATION_MONTHS = 12;
    int MAX_DURATION_MONTHS = 120;

    static int durationOrDefault(Integer durationMonths) {
        int months = durationMonths == null ? DEFAULT_DURATION_MONTHS : durationMonths;
        if (months < 1 || months > MAX_DURATION_MONTHS) {
            throw new IllegalArgumentException("durationMonths는 1~" + MAX_DURATION_MONTHS + " 사이여야 합니다.");
        }
        return months;
    }
}
