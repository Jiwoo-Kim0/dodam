# 진단 실행·조회 API — S15P21D105-544·545·546

작성일 2026-09-13. 544·545는 브랜치 `S15P21D105-544-545-api-14`(병합됨), 546은 `S15P21D105-546-api-14`.
저장 계층은 [diagnosis.md](diagnosis.md), 설계 근거와 미정 규칙은 [544·545 계획](diagnosis-api-plan.md)·[546 계획](diagnosis-goal-impact-plan.md)을 참조한다.

## 현재 구현 범위

명세 14의 9개 API와 목표 위험·저장된 당일 현금흐름 위험을 연결했다. **기획안 전체 탐지 규칙과 군집 분석 엔진은 완료하지 않았다.**
미정 규칙을 임의 임계값으로 실행하지 않고 coverage에 `UNAVAILABLE/RULE_POLICY_NOT_DEFINED`로 표시한다.
군집 모델 학습·실행·근거 병합은 미연동이며, 새 진단 문제는 RULE이다. 기존에 저장된 군집 문제의 조회 형식은 지원한다.
546의 목표영향·진단변화는 저장된 스냅샷과 문제만 비교하며 대출 금융비용 항목은 규칙 미정으로 미제공이다. 최신 Jira 설명은 로컬 CSV(2026-09-07)로 대신 확인했다.

## HTTP 계약

공통 경로 `/api/v1/diagnoses`. 모든 API는 인증 필수이며 `AuthenticatedUserIdResolver`로 본인을 식별한다.
공통 성공 응답은 ApiResponse, 페이지 응답은 PageResponse다.

| Method / 경로 | 입력 | 결과 |
| --- | --- | --- |
| POST | 선택 Body `{goalId?, forceRefresh?}` | `{diagnosis, cached}`. 새 완료본 201, 재사용 200 |
| GET | page=0, size=20 (1~100) | 완료된 진단 이력 페이지 |
| GET /latest | goalId? (양수) | 최신 완료 진단, 없으면 404 |
| GET /{diagnosisId} | 양수 ID | 완료 진단 상세 |
| GET /{diagnosisId}/summary | 양수 ID | `{diagnosis, byDomain, bySeverity}` |
| GET /{diagnosisId}/issues | domain?, type?, severity?, page=0, size=20 | 문제 페이지 |
| GET /{diagnosisId}/issues/{issueId} | 양수 ID 2종 | 문제 상세 |
| GET /{diagnosisId}/goal-impact | 양수 ID | 진단 시점 목표별 영향 (546) |
| GET /{diagnosisId}/changes | 양수 ID | 같은 goalId의 직전 완료본과 비교 (546) |

domain/type은 `^[A-Z][A-Z0-9_]{0,49}$`. 형식상 유효한 미등록 값은 빈 페이지다. severity는 LOW/MEDIUM/HIGH/CRITICAL만 허용한다.
부적절한 ID·페이지·필터·JSON은 공통 400. 없는/타인/미완료 진단, 다른 진단에 속한 문제는 동일 RESOURCE_NOT_FOUND(404).
본인 완료 진단에 문제가 없으면 빈 페이지다. summary는 필터를 적용하지 않은 전체 문제를 집계한다.

이력은 createdAt DESC, diagnosisId DESC. latest는 completedAt DESC, diagnosisId DESC.
문제 페이지는 CRITICAL → HIGH → MEDIUM → LOW, issueId ASC로 DB에서 정렬한다.
goalId 없는 latest는 목표 연계 여부와 관계없이 본인의 최신 완료본이다. POST의 완료본 재사용 조회와 다르다.

## 실행·캐시·실패

- 동기 실행. 접수 시각을 Asia/Seoul 기준일로 사용하며 완료 시각은 계산 후 다시 읽는다.
- goalId 지정 시 삭제되지 않은 본인 목표만 접수한다. 생략 시 ACTIVE 목표 전체를 평가한다.
- forceRefresh 기본 false. 동일 사용자·goalId(null 포함)·규칙 버전의 같은 서비스일 완료본을 재사용한다.
  당일 데이터가 변경돼도 캐시가 유지되므로 강제 재진단이 필요하다. 과거 목표 수정/삭제는 저장된 결과를 바꾸지 않는다.
- 사용자 행 PESSIMISTIC_WRITE 잠금 아래 활성 실행 확인과 접수를 직렬화한다. 계산 중에는 이 잠금을 유지하지 않는다.
- 활성 접수의 유효기간은 **5분 서비스 기본값**이다. 활성 실행은 forceRefresh로 우회할 수 없으며 409 DIAGNOSIS_IN_PROGRESS.
- 완료 시 사용자 잠금 아래 DB 상태를 다시 읽고 미완료·5분 미경과·더 최신 접수 없음 조건을 확인한다.
  만료/대체된 실행은 409 DIAGNOSIS_EXPIRED. 메모리 객체의 완료 여부만으로 판단하지 않는다.
- 계산 실패 시 그 실행의 미완료 헤더와 하위행만 삭제해 즉시 재시도할 수 있다. 완료본·다른 실행은 삭제하지 않는다.
  프로세스가 죽어 정리가 불가능하면 미완료행이 남지만 결과 API에서 숨겨지고 5분 후 재접수가 가능하다.
  별도 실패 이력/상태 컬럼은 추가하지 않았다. 543 저장 계층을 직접 쓰는 내부 경로는 이 잠금 계약을 우회하므로 API는 ExecutionStore만 사용한다.

## 규칙·근거

규칙 버전은 `v3-goal-impact`다(546에서 스냅샷에 목표 예상 달성일을 추가하며 `v2-goal-cashflow`에서 올림). 규칙 변경 때 버전을 변경해야 이전 완료본을 잘못 재사용하지 않는다.

| domain/type | 데이터 출처와 판정 |
| --- | --- |
| GOAL/SAVING_GOAL_RISK | GoalCalculationService 위험 요인. 기한 경과·연결계좌 없음·저축속도 부족·일정 지연을 목표당 한 문제의 근거로 보존 |
| GOAL/SPENDING_GOAL_RISK | 기존 Goal 계산의 실제/예상 월 소비 초과. 목표당 한 문제 |
| CASHFLOW/BALANCE_SHORTAGE | 최신 완료 예측의 baseDate가 진단 기준일과 같은 경우 저장된 위험을 변환. 예측별 위험당 한 문제 |

목표 계산은 접수 기준일을 전달하는 readOnly 경로를 추가했다. 기존 Goal API의 잔액 갱신·이력 기록 계약은 유지하고 진단 경로는 이를 수행하지 않는다.
Goal의 기존 심각도·수치·가정을 재사용하며 진단에서 별도 금액 계산을 추가하지 않는다.
GoalCalculationService의 실제 판정 상수는 benchmarkValue에 보존한다. 입력 스냅샷은 목표 기본/세부 조건과 계산 수치, SAVING 목표의 예상 달성일(`forecast`, projection 제외)을 포함한다.

현금흐름 예측을 진단 중 새로 실행하지 않는다. 없는 예측은 FORECAST_NOT_FOUND, 과거/미래 기준일 예측은 STALE_FORECAST다.
당일 저장 예측에서도 원래 수집 단계의 누락 사유가 영속화되지 않으므로 coverage에 STORED_FORECAST_INPUT_COMPLETENESS_UNKNOWN을 기록한다.
원본 forecastId·기간·위험 수치와 계산 버전을 inputSnapshot에 보존한다. 신규 규칙으로 기존 현금흐름 계산을 복제하지 않는다.

## 응답·데이터 부족

진단 응답: diagnosisId, goalId, createdAt, completedAt, ruleVersion, overallStatus, issueCount, highSeverityCount, coverage, assumptions.
문제 응답: issueId, diagnosisId, domain, type, severity, title, description, currentValue, benchmarkValue, evidence,
detectionSource, modelVersion, clusterContext, detectedAt. RULE의 군집 필드는 null이다.
raw inputSnapshot은 외부 응답에 포함하지 않는다. 조회는 현재 데이터로 재계산하지 않는다.

HIGH/CRITICAL 문제가 있으면 RISK, LOW/MEDIUM만 있으면 CAUTION이다.
**현재 버전은 전체 규칙이 구현되지 않았으므로 문제 0건이어도 overallStatus=null**이며 HEALTHY를 만들지 않는다.
coverage는 실행한 GOAL/CASHFLOW, 대상 없음, 누락 데이터, 미정 규칙·군집 미연동을 구분한다.

미구현 규칙: 휴면 계좌, 고정/변동지출 증가, 고금리 대출, 이자/상환 부담, 저사용 카드,
카드 결제 부담, 저축 후 현금 부족. 문서의 임계값·자료 완전성 확인과 정책 확정이 선행되어야 한다.
군집 조건 검증·동일 카테고리 근거 병합·실제 공급자 연결도 후속 구현이다. 545 전체 군집 흐름을 완료한 것으로 간주하지 않는다.

## 목표 영향·진단 변화 (546)

두 API 모두 `input_snapshot.goals[]`와 저장된 문제만 읽는다. 조회 시점의 목표·잔액·거래로 재계산하지 않으므로 진단 후 목표를 바꿔도 응답이 바뀌지 않는다.
없는/타인/미완료 진단은 다른 조회와 같이 RESOURCE_NOT_FOUND다.

### goal-impact

`{diagnosisId, goalId, asOf, ruleVersion, goals[], unavailable[], assumptions}`. `goals`는 진단이 평가한 목표(goalId 지정 시 1개, 생략 시 당시 ACTIVE 전체)이며 비어 있으면 빈 배열이다.

| 목표별 필드 | 기획안 6-2 항목 | 출처 |
| --- | --- | --- |
| targetAmount, currentAmount, remainingAmount | 목표 대비 부족 금액 | 스냅샷 `forecast` (v2 이전은 목표 원본 스냅샷, remainingAmount null) |
| requiredMonthlySaving, averageMonthlySaving, monthlyShortfall = max(0, 필요 − 평균) | 목표 대비 부족 저축액 | 스냅샷 `metrics` |
| targetDate, expectedAchievementDate, daysDifference, onSchedule | 예상 목표 달성 시점 | 스냅샷 `forecast`. 저축속도 0이면 예상일 null |
| monthlyBudget, spent, projectedMonthEnd, overBudgetAmount = max(0, max(spent, projected) − budget) | 소비 초과 금액 | 스냅샷 `metrics` (SPENDING) |
| riskLevel, factors, relatedIssues[] | 탐지 요인의 영향 | 스냅샷 위험 판정과 `evidence.targetId == goalId`인 GOAL 문제 |
| coverage[] | — | `SNAPSHOT_WITHOUT_FORECAST`: v3 이전 완료본 |

`unavailable`에는 `FINANCING_COST_DELAY/RULE_POLICY_NOT_DEFINED`가 항상 포함된다. 기획안의 "금융비용으로 인한 목표 달성 지연"은 대출 이자·상환 규칙이 확정되지 않아 계산하지 않는다.
SAVING 목표의 소비 필드, SPENDING 목표의 저축 필드는 null이다.

### changes

이전 진단 = 같은 사용자·**같은 goalId(null은 null끼리)**·대상보다 먼저 완료된 완료본 중 최신(completedAt DESC, diagnosisId DESC). 평가 대상 목표 집합이 다른 진단끼리는 비교하지 않는다.
이전 진단이 없으면 200이며 `previousDiagnosisId=null, comparable=false`, 배열은 비어 있다.

- `statusChange.direction`: HEALTHY < CAUTION < RISK 기준 IMPROVED/WORSENED/UNCHANGED. 어느 한쪽이 null(부분 평가)이면 UNKNOWN.
- `issueChanges`: 문제 동일성 키는 issueId가 아니라 `domain:type:` + (`evidence.riskDate` 있으면 그 날짜, 아니면 `evidence.targetId`, 둘 다 없으면 `issue-<issueId>`).
  CASHFLOW의 riskId·forecastId는 예측 재실행마다 새로 발급돼 키에서 제외한다. added/resolved/changed(심각도 변경)/unchangedCount.
- `goalChanges`: 양쪽 스냅샷에 모두 있는 목표만. remainingAmount·requiredMonthlySaving·averageMonthlySaving·monthlyShortfall·spent·projectedMonthEnd·overBudgetAmount(원 delta), expectedAchievementDate(일 delta), riskLevel(delta null). 값이 같으면 기록하지 않고, 한쪽만 null이면 delta null.
- `ruleVersionChanged=true`면 규칙 추가로 생긴 added를 악화로 해석하지 않는다.

## 검증

DiagnosisApiTest는 실제 H2 저장·HTTP 조회로 빈 입력·캐시·강제 실행, 목표 진단·요약,
과거 결과 불변성, 7개 경로 인증, 소유권, 필터·페이지 검증, 심각도 페이지 정렬과 저장 군집 근거 조회,
실패 정리·만료 후 재접수·동시 접수를 검증한다. 기존 Diagnosis 저장/Repository와 Goal 계산 회귀 테스트도 실행한다.
MySQL 테스트는 DIAGNOSIS_MYSQL_TEST_URL이 설정된 경우에만 실행된다. H2 동시성 테스트만으로 MySQL 잠금 검증 완료를 주장하지 않는다.

2026-09-13 검증 결과: JDK 21에서 관련 Diagnosis/Goal 테스트 및 `gradlew.bat clean build` 통과.
전체 840건 중 833건 성공, 7건 조건부 건너뜀, 실패/오류 0건. 새 DiagnosisApiTest 7건 성공.
DiagnosisMySqlIntegrationTest 3건은 연결 환경변수 미설정으로 건너뛰었다. `git diff --check` 공백 오류 없음.

546 검증(2026-09-13): DiagnosisGoalImpactApiTest 4건 — 스냅샷 기반 goal-impact와 목표 변경 후 불변성, 첫 진단의 비교 불가,
같은 goalId 범위의 이전 진단 선택과 키 기반 resolved·riskLevel 변화, CASHFLOW 키의 riskId 무관성과 v2 스냅샷 coverage, 인증·소유권·미완료·ID 검증.
`gradlew.bat clean build` 통과: 전체 844건 중 837 성공, 7건 조건부 건너뜀, 실패 0. MySQL 검증은 새 DDL이 없어 생략했다(JPQL 이전 진단 조회는 H2만 확인).
