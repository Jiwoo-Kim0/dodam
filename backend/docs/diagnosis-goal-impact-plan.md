# Diagnosis 546 착수 판단과 구현 계획

작성일: 2026-09-13. 구현 전 계획이며 완료된 기능을 설명하지 않는다.

> 진행 결과는 [diagnosis-api.md](diagnosis-api.md)의 546 절을 따른다. 544 MR은 이 계획 작성 직후 develop에 병합돼 아래 "544·545 미커밋" 전제는 해소됐다.
544·545 계약은 [diagnosis-api.md](diagnosis-api.md), 저장 계층은 [diagnosis.md](diagnosis.md), 목표 계산은 [goal.md](goal.md)를 따른다.

## 판단

**착수 가능하다. 단, 544·545 변경이 아직 커밋되지 않은 상태라 같은 브랜치(`S15P21D105-544-545-api-14`)에서 이어서 작업하거나, 544·545 커밋 후 연쇄 분기해야 한다.**

| 확인 항목 | 결과 |
| --- | --- |
| Jira 범위 | `SPRINT/Jira (4).csv`(2026-09-07 내보내기): S15P21D105-546 `[API 14] 목표영향·진단변화`, 설명 "goal-impact·changes 구현", 상위 466 `[D8] Diagnosis 완료 및 Recommendation 기반 구현`, 라벨 `sprint4,day8,be-b,api-14`. 최신 Jira는 조회하지 못했다 |
| API 명세 | `GET /api/v1/diagnoses/{diagnosisId}/goal-impact`(자체 GoalImpact 계산), `GET /api/v1/diagnoses/{diagnosisId}/changes`(자체 비교). 둘 다 Path diagnosisId, 인증 필수, 본인 리소스만. 응답 DTO는 명세에 없다 |
| 기획안 근거 | 6-2 목표 달성 영향 분석(예상 달성 시점, 탐지 요인의 영향, 부족 금액, 부족 저축액, 소비 초과 금액, 금융비용 지연), 6-3 "이전 진단 대비 변화" |
| ERD | `diagnosis`·`diagnosis_issue` 외에 목표영향·변화 전용 테이블이 없다(`simulation_goal_impact`는 Simulation 소관). **새 테이블·마이그레이션 없이 저장된 진단으로 계산한다** |
| 선행 코드 | 543(엔티티·Repository)은 develop에 병합됨. 544·545(Controller·QueryService·Evaluator·ExecutionStore·DiagnosisApiTest)는 **로컬 미커밋**. `DiagnosisIssueRepository.findAllOwned`가 이미 "요약·목표영향 계산" 용도로 준비돼 있다 |
| Goal 연동 | `GoalCalculationService.getRisk(userId, goalId, today)` readOnly 경로가 544에서 추가됨. 예상 달성일 `forecast`는 아직 기준일 인자를 받는 readOnly 경로가 없고 진단 스냅샷에도 없다 |
| 브랜치 | HEAD `7c462fa` = `origin/develop` 최신. 원격에 `origin/S15P21D105-544-545-api-14` 존재 |

### 착수를 막지 않는 미정 사항

- 기획안 6-2의 "금융비용으로 인한 목표 달성 지연"은 대출 이자·상환 규칙(`LOAN_INTEREST_BURDEN` 등)이 544에서 `RULE_POLICY_NOT_DEFINED`라 계산 근거가 없다. 임의 수치로 만들지 않고 `UNAVAILABLE`로 표시한다.
- 응답 DTO·비교 기준(무엇을 "이전"으로 볼지, 문제 동일성 키)은 명세에 없다. 아래 기본안을 적용하고 문서에 명시한다.

## 범위

| 이슈 | 구현 대상 |
| --- | --- |
| 546 | `GET /{diagnosisId}/goal-impact`, `GET /{diagnosisId}/changes`, 응답 DTO 2종, 스냅샷 보강(예상 달성일), 테스트, 문서 |
| 제외 | 추천 엔진(547), 시뮬레이션 목표영향(17 Simulation), 대출 금융비용 규칙, 군집 연동, 목표 이력(`goal_history`) 기반 장기 추세 |

## 핵심 설계 결정 (기본안)

### 1. 저장된 스냅샷으로 계산하고 현재 데이터로 재계산하지 않는다

544 계약("조회는 현재 데이터로 재계산하지 않는다", "과거 목표 수정/삭제는 저장된 결과를 바꾸지 않는다")을 그대로 잇는다.
goal-impact와 changes 모두 `diagnosis.input_snapshot`과 `diagnosis_issue`만 읽는다. 진단 시점의 목표가 이후 수정·삭제돼도 응답이 바뀌지 않으며, changes의 두 진단이 각각 자기 시점의 값으로 비교된다.

따라서 **스냅샷에 없는 값은 만들 수 없다.** 현재 `snapshot.goals[]`에는 `GoalRiskResponse`(riskLevel·factors·metrics 4종)와 `goalSnapshot`·`savingGoalSnapshot`·`spendingGoalSnapshot`이 있고, 예상 달성일·남은 금액·`daysDifference`가 없다.

### 2. 스냅샷 보강: 예상 달성일을 진단 시점에 함께 보존한다

- `GoalCalculationService`에 `getForecast(userId, goalId, baseDate)`의 readOnly·잔액 미갱신·이력 미기록 변형을 추가한다(544가 `getRisk`에 한 것과 같은 방식, `projection`은 저장하지 않거나 0개월).
- `DiagnosisEvaluator`가 SAVING 목표마다 `forecast`(remainingAmount, averageMonthlySaving, monthsToAchieve, expectedAchievementDate, daysDifference, onSchedule)를 `goals[]` 항목에 추가한다. SPENDING은 기존 metrics(monthlyBudget·spent·projectedMonthEnd)로 충분하다.
- Goal 계산식을 진단에서 복제하지 않는다. 남은 금액 = targetAmount − currentAmount 같은 단순식도 Goal 응답값을 저장해 쓴다.
- 규칙 버전을 `v2-goal-cashflow` → `v3-goal-impact`로 올린다. 스냅샷 항목이 바뀌므로 당일 완료본 재사용을 끊어야 한다.
- v2 이하 완료본(스냅샷에 forecast 없음)은 goal-impact에서 해당 필드를 null로 두고 `coverage`에 `SNAPSHOT_WITHOUT_FORECAST`를 표시한다. 오류로 만들지 않는다.

이 변경은 544의 Evaluator를 건드리지만 같은 브랜치의 미커밋 코드이고, 546이 요구하는 데이터를 544 실행 시점에 남기는 유일한 방법이라 546 범위로 본다.

### 3. goal-impact 응답

```
{
  diagnosisId, goalId(진단 요청값), asOf(진단 createdAt), ruleVersion,
  goals: [{
    goalId, name, type(SAVING|SPENDING), status, riskLevel, factors[],
    // SAVING
    targetAmount, currentAmount, remainingAmount,            // 기획안 "목표 대비 부족 금액"
    requiredMonthlySaving, averageMonthlySaving, monthlyShortfall,  // "목표 대비 부족 저축액" = max(0, required − average)
    targetDate, expectedAchievementDate, daysDifference, onSchedule, // "예상 목표 달성 시점"
    // SPENDING
    monthlyBudget, spent, projectedMonthEnd, overBudgetAmount,      // "소비 초과 금액" = max(0, max(spent, projected) − budget)
    relatedIssues: [{ issueId, domain, type, severity }]            // "탐지된 요인이 목표 달성에 미치는 영향": 해당 목표의 GOAL 문제
  }],
  unavailable: [{ item: "FINANCING_COST_DELAY", reason: "RULE_POLICY_NOT_DEFINED" }],
  assumptions: [...]
}
```

- `goals`가 비어 있으면(진단 시점 활성 목표 없음) 빈 배열과 200이다. 404가 아니다.
- 원 단위는 `Long`, 비율은 스냅샷의 `Double`을 그대로 전달한다. 새 비율 계산은 추가하지 않는다.
- `relatedIssues`는 `evidence.targetId == goalId && domain == GOAL`로 연결한다. issueId는 응답에만 쓰고 비교 키로는 쓰지 않는다.
- 소유권·완료 여부 검증은 `DiagnosisQueryService.owned`를 재사용한다. 없는/타인/미완료 진단은 RESOURCE_NOT_FOUND.

### 4. changes 응답과 비교 기준

**이전 진단** = 같은 사용자, 같은 `goalId`(null 포함 완전 일치), 완료본 중 대상 진단보다 먼저 완료된 것 가운데 최신(completedAt DESC, diagnosisId DESC).
goalId가 다른 진단은 평가 대상 목표 집합이 달라 문제 수 증감이 의미를 갖지 않으므로 비교하지 않는다.
Repository에 `findFirstByUserIdAndGoalIdAndCompletedAtIsNotNullAndCompletedAtLessThan...`(goalId null 변형 포함) 또는 `@Query` 1건을 추가한다.

```
{
  diagnosisId, previousDiagnosisId(null 가능), comparable(boolean), ruleVersionChanged(boolean),
  current:  { completedAt, ruleVersion, overallStatus, issueCount, highSeverityCount },
  previous: { ... } | null,
  statusChange: { from, to, direction: IMPROVED|WORSENED|UNCHANGED|UNKNOWN },
  issueChanges: {
    added:    [{ key, issueId, domain, type, severity }],
    resolved: [{ key, previousIssueId, domain, type, severity }],
    changed:  [{ key, issueId, previousIssueId, domain, type, severity, previousSeverity }],
    unchangedCount
  },
  goalChanges: [{ goalId, type, metric, previous, current, delta }]   // 예: remainingAmount, monthlyShortfall, expectedAchievementDate, overBudgetAmount
}
```

- 이전 진단이 없으면 `previous=null`, `comparable=false`, 나머지 배열은 비어 있고 200이다. 첫 진단은 정상 상태이지 오류가 아니다.
- **문제 동일성 키**는 `issueId`가 아니라 근거 JSON으로 만든다(544 계획서에서 546을 위해 남겨 둔 `ruleKey`·`targetId`).
  - GOAL: `domain + type + evidence.targetId(goalId)`
  - CASHFLOW: `domain + type + evidence.riskDate`. `evidence.targetId`(riskId)와 `forecastId`는 예측마다 새로 발급되므로 키에서 제외한다.
  - 키를 만들 수 없는 행(과거 형식·군집 fixture)은 `domain + type + issueId`로 두어 항상 added/resolved로 보인다. 조용히 버리지 않는다.
- `overallStatus` 순서는 HEALTHY < CAUTION < RISK. 어느 한쪽이 null(부분 평가)이면 `UNKNOWN`.
- `ruleVersion`이 다르면 비교는 하되 `ruleVersionChanged=true`로 표시한다. 규칙 추가로 생긴 added를 악화로 해석하지 않도록 문서에 적는다.
- `goalChanges`는 양쪽 스냅샷에 모두 있는 goalId만 비교한다. 한쪽에만 있는 목표는 `goalChanges`에 넣지 않고 `issueChanges`로만 드러난다.
- 날짜 delta는 일 수(`long`), 금액 delta는 원(`long`).

### 5. 구조

- `DiagnosisController`에 GET 2개 추가. 기존 `@Positive` 검증·`AuthenticatedUserIdResolver` 사용.
- `DiagnosisQueryService`에 넣지 않고 `DiagnosisGoalImpactService`·`DiagnosisChangeService`(`@Transactional(readOnly = true)`)를 둔다. 스냅샷 JSON 해석 로직이 조회 서비스와 섞이면 커진다.
- 스냅샷 JSON → 값 변환은 `GoalImpactSnapshotReader` 같은 내부 클래스 하나로 모아 v2/v3 형식 차이를 한 곳에서 처리한다.
- DTO는 `dto/DiagnosisGoalImpactResponse`, `dto/DiagnosisChangesResponse`.

## 구현 순서

1. **스냅샷 보강** — `GoalCalculationService` readOnly forecast, Evaluator `goals[].forecast`, `DiagnosisRuleVersion` v3. 기존 DiagnosisApiTest·Goal 테스트 통과 확인.
2. **goal-impact** — 스냅샷 리더, 서비스, DTO, Controller. 테스트: SAVING/SPENDING 혼합, 목표 없음, v2 스냅샷, 인증 실패, 타인/미완료 진단.
3. **changes** — 이전 진단 조회 쿼리, 키 생성, 비교 서비스, DTO, Controller. 테스트: 첫 진단, 문제 추가/해소/심각도 변경, goalId 다른 진단 무시, 상태 변화 방향, ruleVersion 변경 표시, CASHFLOW 키가 riskId에 의존하지 않음, 인증·소유권.
4. **검증** — 관련 테스트 → `gradlew.bat test` → `clean build`, `git diff --check`. MySQL은 JSON 읽기만 추가되므로 별도 마이그레이션 검증은 없다.
5. **문서** — `diagnosis-api.md`에 9개 API 계약으로 확장(현재 "546은 범위 밖" 문구 제거), `remaining-work.md` 갱신. 루트 `docs/` API 명세는 수정하지 않는다.

## 위험과 선행 조건

- **544·545 미커밋**: 546 작업 전에 544·545를 먼저 커밋해 diff를 분리하는 것을 권장한다. 커밋은 사용자 요청 시 수행한다.
- 최신 Jira 설명·완료 조건을 확인하지 못했다. 로컬 CSV(2026-09-07)와 API 명세 기준 계획이다.
- 규칙 버전 상승으로 v2 당일 완료본은 재사용되지 않고 새 진단이 만들어진다. 배포 전 로컬 데이터만 영향받는다.
- "금융비용으로 인한 목표 달성 지연"은 대출 규칙 정책 확정 전까지 `UNAVAILABLE`이다. 완료 보고 시 이 항목을 완료로 표시하지 않는다.
- 이전 진단이 goalId 완전 일치 기준이라, 전체 진단(goalId=null)과 특정 목표 진단 사이의 변화는 제공하지 않는다. FE가 목표 화면에서 변화를 보려면 같은 goalId로 재진단해야 한다.
