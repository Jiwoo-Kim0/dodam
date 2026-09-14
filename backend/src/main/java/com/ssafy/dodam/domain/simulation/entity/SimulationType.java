package com.ssafy.dodam.domain.simulation.entity;

/**
 * 시뮬레이션 유형. `chk_simulation_type`과 같은 10종이다.
 *
 * <p>값이 있다고 해서 계산기가 있는 것은 아니다. 계산기가 등록되지 않은 유형은 HTTP 경로 자체를 열지 않으며,
 * 목록 조회 필터에서만 모든 값을 받는다.
 */
public enum SimulationType {
    SPENDING,
    SAVING,
    SAVING_PRODUCT,
    LOAN_PREPAYMENT,
    LOAN_REFINANCE,
    LOAN_NEW,
    INSTALLMENT,
    REVOLVING,
    CASH_ADVANCE,
    COMBINED
}
