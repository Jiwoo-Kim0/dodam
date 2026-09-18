package com.ssafy.dodam.domain.diagnosis.service;

/**
 * 진단 규칙 버전(`diagnosis.rule_version`).
 *
 * <p>같은 사용자·같은 데이터라도 규칙이 바뀌면 결과가 달라진다. 어느 규칙으로 나온 진단인지 남지
 * 않으면 과거 진단을 재현하거나 비교(546의 `changes`)할 수 없다.
 *
 * <p>표기 규칙은 팀에서 정하지 않았다. cashflow의 `CashflowCalculationVersion`과 같은 상황이라 같은
 * 형태의 상수로 두고 remaining-work.md에 남긴다. 규칙을 바꿀 때 이 값을 올리지 않으면 비교가 깨진다.
 *
 * <p>이력: {@code v5-copy-tone}(627 문구 정비, 소비 증가 = 최근 3개월 평균 vs 이전 3개월 평균) →
 * {@code v6-last-month-vs-3m}(631, 소비 증가 = 지난달 vs 직전 3개월 평균). 진단 캐시({@code DiagnosisExecutionStore})가 버전별이라
 * 올린 뒤 첫 진단 요청은 새 행을 만들고 이전 버전 진단·추천은 그대로 남는다.
 */
public final class DiagnosisRuleVersion {

    public static final String CURRENT = "v6-last-month-vs-3m";

    private DiagnosisRuleVersion() {
    }
}
