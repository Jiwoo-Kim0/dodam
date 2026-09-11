package com.ssafy.dodam.domain.diagnosis.entity;

/**
 * 탐지 문제의 심각도(`diagnosis_issue.severity`).
 *
 * <p>ERD 주요 코드값 표의 공통 심각도 집합이며 `cashflow_risk.severity`와 값이 같다. 그럼에도
 * `cashflow`의 `RiskSeverity`를 재사용하지 않고 별도 enum을 둔다. 한쪽 도메인의 코드값 enum을
 * 다른 도메인이 import하면 두 도메인이 서로의 코드값 변경에 묶인다.
 *
 * <p>두 도메인이 실제로 같은 집합을 계속 공유한다면 `global/`로 올리는 편이 낫다. 지금 옮기지 않는
 * 것은 cashflow 쪽 파일을 건드리면 병합 대기 중인 S15P21D105-542와 충돌하기 때문이다.
 * 통합 여부는 remaining-work.md에 남긴다.
 *
 * <p>선언 순서가 곧 심각도 순서다. `severity`가 문자열 컬럼이라 SQL 사전순은 심각도순과 다르므로,
 * 정렬이 필요하면 이 순서를 기준으로 한다(542가 `cashflow_risk`에서 택한 방식과 같다).
 */
public enum IssueSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
