package com.ssafy.dodam.domain.simulation.entity;

/**
 * `chk_simulation_status`의 4종. 현재 실행은 요청 안에서 동기로 끝나므로 저장되는 행은 항상 {@link #COMPLETED}다.
 * 계산이 실패하면 행을 남기지 않는다(FAILED 미사용). 비동기 실행이 생기면 그때 PENDING·RUNNING을 쓴다.
 */
public enum SimulationStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
