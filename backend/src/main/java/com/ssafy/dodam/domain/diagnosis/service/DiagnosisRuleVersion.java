package com.ssafy.dodam.domain.diagnosis.service;

/**
 * 진단 규칙 버전(`diagnosis.rule_version`).
 *
 * <p>같은 사용자·같은 데이터라도 규칙이 바뀌면 결과가 달라진다. 어느 규칙으로 나온 진단인지 남지
 * 않으면 과거 진단을 재현하거나 비교(546의 `changes`)할 수 없다.
 *
 * <p>표기 규칙은 팀에서 정하지 않았다. cashflow의 `CashflowCalculationVersion`과 같은 상황이라 같은
 * 형태의 상수로 두고 remaining-work.md에 남긴다. 규칙을 바꿀 때 이 값을 올리지 않으면 비교가 깨진다.
 */
public final class DiagnosisRuleVersion {

    public static final String CURRENT = "v3-goal-impact";

    private DiagnosisRuleVersion() {
    }
}
