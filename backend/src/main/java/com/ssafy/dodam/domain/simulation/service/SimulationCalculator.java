package com.ssafy.dodam.domain.simulation.service;

import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.simulation.dto.SimulationInput;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;

/**
 * 유형별 계산기. 순수 함수여야 한다 — 저장·외부 호출·시각 조회 없이 기준선과 입력만으로 결과를 만든다.
 * 그래야 DB 없이 단위 테스트가 돌고, Runner의 잠금 트랜잭션 안에서 부작용 없이 실행할 수 있다.
 *
 * <p>기준선이 있어야 판단할 수 있는 검증(감소액 ≤ 평균 지출 등)은 여기서 {@code BusinessException}으로 거부한다.
 *
 * @param <Q> 확정된 입력 타입
 */
public interface SimulationCalculator<Q extends SimulationInput> {
    SimulationType type();

    Class<Q> inputType();

    SimulationResult calculate(SimulationBaseline baseline, Q input);
}
