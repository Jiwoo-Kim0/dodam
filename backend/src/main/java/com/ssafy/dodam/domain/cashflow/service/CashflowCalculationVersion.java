package com.ssafy.dodam.domain.cashflow.service;

/**
 * 예측 계산 로직의 버전 표기.
 *
 * <p>같은 기준일이라도 계산 규칙이 바뀌면 결과가 달라진다. 저장된 예측이 어느 규칙으로 계산됐는지
 * 남겨야 캐시 재사용(S15P21D105-540)과 결과 비교가 가능하므로 NULL을 남기지 않는다.
 *
 * <p>TODO(S15P21D105-540): 팀에서 버전 표기 규칙이 정해지면 상수를 교체한다.
 */
public final class CashflowCalculationVersion {

    public static final String CURRENT = "v1";

    private CashflowCalculationVersion() {
    }
}
