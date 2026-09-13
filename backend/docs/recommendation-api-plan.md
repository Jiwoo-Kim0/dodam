# Recommendation API 구현 계획 — S15P21D105-548 · 549

작성일: 2026-09-13. 구현 전 계획이며 같은 날 구현을 마쳤다. 구현 계약은 [recommendation-api.md](recommendation-api.md)가 기준이다.
547 저장 기반([recommendation.md](recommendation.md), [recommendation-foundation-plan.md](recommendation-foundation-plan.md)) 위에서
HTTP API 13개를 완성한다.

## 1. 착수 판단과 근거

**548과 549는 한 흐름으로 진행한다.** 같은 도메인·같은 3개 테이블·같은 Controller를 다루고, 549의 상태 전이는
547이 만든 `RecommendationHistory` 초기 이력 저장을 확장하는 것이라 548의 조회 DTO를 그대로 재사용한다.
나눠서 진행하면 `RecommendationController`·응답 DTO·문서를 두 번 건드리게 된다.

| 이슈 | 로컬 이슈 범위 (`SPRINT/Jira (4).csv`, 2026-09-07 내보내기) | 이번 계획의 산출물 |
| --- | --- | --- |
| 548 | 생성·목록·요약·우선순위·상세·근거·효과·목표기여 구현 | 운영 생성기 3종, 효과·우선순위 계산, GET/POST 8개 |
| 549 | accept·reject·complete·defer·history 구현 | 상태 전이 정책, POST 4개 + GET history |

최신 Jira 설명·완료 조건과 대조는 구현 시작 전에 다시 한다(이전 연결 시 403).

### 선행 조건과 의존 관계

| 선행 | 상태 (2026-09-13) | 영향 |
| --- | --- | --- |
| 547 `S15P21D105-547-api-16-recommendation` | 4개 커밋 push 완료, `develop` 미병합 | **필수.** V18·엔티티·Store·Reader 전부 의존 |
| 546 `S15P21D105-546-api-14` | push 완료, `develop` 미병합 | **의존 없음.** 예상 달성일은 스냅샷의 `targetAmount·currentAmount·averageMonthlySaving`으로 생성기가 직접 계산한다(4.1) |
| 544·545 (`b90ddb8`) | `origin/develop` 포함 | 완료 진단·문제 목록 입력 |

2026-09-13 실제 진행: Jira가 만든 `S15P21D105-548-api-16`은 `develop`(53e37b8) 기준이라 547이 없었다. `.worktrees/548`에서
`origin/S15P21D105-547-api-16-recommendation`을 merge(6ef380a, 충돌 없음)해 기반을 갖췄다. 547 MR이 먼저 병합되면 548 MR diff에서 547 커밋은 사라진다.
546의 `forecast`는 읽지 않는다 — 546 이전 진단(v2)과 이후 진단(v3)에서 같은 결과가 나와야 하고, 현재·개선 달성일을 **같은 식·같은 기준일**로
계산해야 `advancedDays`가 일관되기 때문이다. 546의 "재계산하지 않는다"는 진단 조회 계약이지 추천 생성 계약이 아니다.

### 현재 진단이 만드는 문제 유형 (생성기 범위의 상한)

[diagnosis-api.md](diagnosis-api.md) §규칙·근거 기준으로 진단은 **3개 유형**만 만든다. 기획안 5-3의 대출·카드·계좌·예적금 추천은
대응하는 진단 규칙(고금리 대출, 저사용 카드, 휴면 계좌 등)이 미구현이라 **이번에 만들 수 없다.** 생성기 확장점만 두고 문서에 제한사항으로 남긴다.

| 진단 domain/type | 근거로 쓸 수 있는 값 | 548 생성기 |
| --- | --- | --- |
| GOAL/SAVING_GOAL_RISK | `currentValue`(=`GoalRiskResponse.Metrics`): `requiredMonthlySaving·averageMonthlySaving`. `evidence.goalSnapshot.currentAmount`, `evidence.savingGoalSnapshot.targetAmount·targetDate` | 월 저축 증액 |
| GOAL/SPENDING_GOAL_RISK | `currentValue`: `monthlyBudget·spent·projectedMonthEnd` | 소비 감축 |
| CASHFLOW/BALANCE_SHORTAGE | `currentValue`(=`CashflowRiskResponse`): `riskId·riskDate·severity·message·expectedBalance`, `evidence.forecastId` | 부족 시점 대비 자금 확보 |

evidence 구조는 `DiagnosisEvaluator.evaluate`가 넣는 형식 그대로다(GOAL: risk 응답 deepCopy + goalSnapshot·savingGoalSnapshot·spendingGoalSnapshot + ruleKey·targetId,
CASHFLOW: risk 응답 + ruleKey·forecastId·targetId). `remaining`·`forecast` 같은 파생값은 없다.

기준 자료: [API 16](../../docs/API명세서/16%20Recommendation%203cdbb6ea3564801eb1bbc55201aee86a.csv),
[ERD SQL](../../docs/ERD/DODAM_ERDCLOUD_IMPORT.sql) 905~979행, [기획안](../../docs/기획안/기획안%20작성%203c3bb6ea35648097a693e0e321b3e9c0.md) 4-5·5-3·5-4·5-6,
[Goal 계산](../src/main/java/com/ssafy/dodam/domain/goal/service/GoalCalculationService.java) `forecast()` 계산식.

## 2. 범위

### 포함

1. 운영 `RecommendationCandidateGenerator` 3종과 효과(`Recommendation.Effect`) 계산.
2. 우선순위 점수와 `priority` 부여 규칙.
3. `RecommendationController` + 응답 DTO: 생성·목록·요약·우선순위·상세·근거·효과·목표기여 (548), 상태 변경 4종·이력 (549).
4. 전용 `ErrorCode` 3개, 상태 전이 정책, 사용자 행 잠금으로 중복 요청 직렬화.
5. H2 통합 테스트(정상·검증·인증·소유권·빈 결과·경계값·전이·멱등)와 `recommendation.md` 갱신 + `recommendation-api.md` 신규.

### 제외 (후속)

- 대출·카드·계좌·예적금 추천 규칙 (진단 규칙 선행), 군집 가중치 α (군집 미연동 → α=0 고정).
- `improvement_action` 생성 (Improvement, Day 11). ACCEPT 시 행동 생성 훅은 두지 않는다.
- Simulation 연동(추천 → 시뮬레이션 실행). 550·551 별도.
- 공개 API 명세(루트 `docs/`)의 요청 모양 변경.

## 3. 브랜치와 worktree

- 548: `S15P21D105-548-api-16` (Jira 생성, `develop` 기준) + 547 merge 커밋. worktree `.worktrees/548`. push는 요청 시.
- 549: 548 커밋 위에서 Jira가 만든 549 브랜치로 연쇄 분기(549 브랜치도 develop 기준으로 생성되므로 같은 방식으로 548을 merge). 548 MR이 먼저 병합돼야 549 MR을 develop으로 낼 수 있다.
- 작업은 `.worktrees/548`(549는 같은 worktree에서 브랜치 전환) — 현재 546 체크아웃(미추적 파일 있음)과 547 worktree를 건드리지 않는다.
- 새 마이그레이션 없음. V18 그대로.

## 4. 548 구현 순서

### 4.1 생성기 3종 (`service/generator/`)

모두 순수 함수(547 계약: 저장·외부 호출·시각 조회 없음). `version()`은 `"548.1"`로 시작하고 계산식이 바뀌면 올린다 — generation_key 공간이 갈린다.
`candidateKey`는 `<type>:<targetId>`(goalId 또는 forecastId)라 같은 진단에서 재생성해도 멱등이다.

| 생성기 | domain/type | title·description 근거 | Effect 계산 |
| --- | --- | --- | --- |
| `SavingGoalRiskGenerator` | SAVING / INCREASE_MONTHLY_SAVING | "목표 저축액 월 R원, 현재 평균 A원 → 월 (R−A)원 추가 저축 필요" (기획안 5-3 예·적금) | `contributionAmount = R − A`(>0일 때만 후보 생성). `remaining = targetAmount − currentAmount`(스냅샷). base = 진단 `createdAt` 날짜(위험 판정 asOf와 동일). `GoalCalculationService.forecast()`와 같은 식으로 `current = base + ceil(remaining / A)`(A ≤ 0이면 null), `improved = base + ceil(remaining / R)`. `advancedDays = current − improved`(current null이면 null). `remaining ≤ 0`이면 후보 없음. 스냅샷에 금액 필드가 없으면(형식 드리프트) 날짜 3개 null + reason `SNAPSHOT_FIELD_MISSING` |
| `SpendingGoalRiskGenerator` | SPENDING / REDUCE_SPENDING | "예산 B원 대비 예상 지출 P원 → 월 (P−B)원 감축 필요" | `monthlyCostSaving = max(0, max(spent, projected) − budget)`(546 goal-impact의 overBudgetAmount와 동일 식). `annualCostSaving = monthly × 12` — **이 생성기에서만** 정의하며 근거는 "월 예산 초과는 매월 반복되는 비용"이라는 계약을 문서에 명시. 날짜 효과 null |
| `CashflowShortageGenerator` | CASHFLOW / SECURE_SHORTAGE_BUFFER | "M월 D일 잔액 부족 예상 S원 → 해당 일 전 S원 확보 필요" | 부족액 `S = max(0, −expectedBalance)`, 부족일 = `riskDate`. `riskReduction = "BALANCE_SHORTAGE_AVOIDED"`(VARCHAR 100 코드값), 금액 효과 null. `candidateKey = SECURE_SHORTAGE_BUFFER:<riskId>`. `evidence`에 forecastId·riskId·riskDate·S 보존 |

각 후보의 `RecommendationCandidate.Reason`은 코드 1~3개(`GOAL_SAVING_SHORTFALL`, `GOAL_FORECAST_DELAY`, `SPENDING_OVER_BUDGET`, `CASHFLOW_SHORTAGE`)와 `evidence` JSON(사용한 metrics 그대로).

주의: 문제의 `evidence` JSON 구조는 `DiagnosisEvaluator`가 넣는 형식이므로 생성기 테스트에 **실제 진단 실행으로 만든 스냅샷**을 하나 고정해 쓴다(형식 드리프트 감지).

### 4.2 우선순위 (`service/RecommendationPriorityPolicy`)

기획안 5-4 기준을 결정적 정수 점수로 만든다. 군집 α=0.

| 기준 | 점수 |
| --- | --- |
| 금융 위험도 | 원본 문제 severity: CRITICAL 40 / HIGH 30 / MEDIUM 20 / LOW 10 |
| 예상 개선 효과 | `monthlyCostSaving`과 `contributionAmount` 중 큰 값을 10만 원 단위로 올림, 최대 30 |
| 목표 기여도 | `advancedDays` 30일당 5점, 최대 20. null이면 0 |
| 시급성 | CASHFLOW `riskDate − 진단 createdAt` 14일 이내 +20, 30일 이내 +10. 이미 지난 날짜도 14일 이내로 본다 |
| 실행 난이도 | SPENDING/SAVING 행동은 즉시 가능 +5, (후속) 대환·해지류 0 |

`priority`는 **한 생성 요청 안에서 점수 내림차순 순위(1부터)**로 저장한다(동점은 issueId ASC). 점수 자체는 컬럼이 없으므로 `RecommendationReason`이 아니라
응답 DTO에만 `score`로 노출한다. 서로 다른 진단에서 만든 추천의 priority는 비교하지 않는다 → priorities API는 **최신 완료 진단 기준**으로 범위를 좁힌다(4.4).
멱등 재생성으로 기존 행이 돌아올 때 priority를 갱신하지 않는다(547 불변 계약).

### 4.3 생성 API

`POST /api/v1/recommendations/generate` Body `{ diagnosisId, goalId? }` → 200 `ApiResponse<RecommendationGenerationResponse>`.

흐름: `RecommendationInputReader.read` → `RecommendationCandidateCollector.collect` → 점수·순위 → `RecommendationStore.save(now)`.
응답: `diagnosisId, goalId, created(신규 건수), reused(기존 건수), recommendations[](상세 DTO), skipped[](547 `SkipReason` 그대로), coverage[]`.
후보 0건이면 빈 목록 + skipped로 이유를 드러낸다(문제 0건이면 skipped도 빈 목록).

전용 ErrorCode(기존 `INVALID_REQUEST` 대체):

| 코드 | HTTP | 발생 |
| --- | --- | --- |
| `DIAGNOSIS_NOT_COMPLETED` | 409 | `completed_at` NULL 진단 |
| `RECOMMENDATION_GOAL_MISMATCH` | 400 | goalId가 진단 goalId와 다름 |
| `RECOMMENDATION_STATUS_TRANSITION_NOT_ALLOWED` | 409 | 549 상태 전이 |

`INVALID_REQUEST`를 던지는 547의 Reader·Store는 새 코드를 쓰도록 바꾼다(해당 테스트 기대값 함께 수정). 타인·없는 진단은 `RESOURCE_NOT_FOUND` 유지.
동시 요청은 547 사용자 행 잠금으로 직렬화되고 두 번째 요청은 `reused`로 같은 행을 받는다 — "중복 요청 방지"는 이 멱등 계약으로 충족한다.

### 4.4 조회 API

공통: `AuthenticatedUserIdResolver`, 소유권 불일치는 `RESOURCE_NOT_FOUND`, 조회 서비스 `@Transactional(readOnly = true)`. 현재 데이터로 재계산하지 않고 저장값만 반환한다.

| Endpoint | 파라미터·검증 | 응답 | 정렬·빈 결과 |
| --- | --- | --- | --- |
| `GET /recommendations` | `status?`(enum 외 400), `domain?`(자유 문자열 ≤50), `page≥0`, `size 1~100` 기본 20 | `PageResponse<RecommendationResponse>` | `createdAt DESC, id DESC`. 빈 페이지 |
| `GET /recommendations/summary` | 없음 | `total, byStatus{5종 모두 0 포함}, byDomain{}, expectedMonthlyCostSaving, expectedContributionAmount`(PENDING+ACCEPTED 합, null 제외) | 추천 0건이면 0으로 채운 객체 |
| `GET /recommendations/priorities` | `goalId?`(타인·삭제 목표 → 404) | 최신 완료 진단(goalId 있으면 그 목표의 최신 완료 진단 — 기존 `DiagnosisRepository`의 goal 조회는 `ruleVersion`을 요구하므로 버전 무관 메서드를 추가)의 PENDING·DEFERRED 추천을 `priority ASC, id ASC` | 완료 진단 없음·추천 없음 → 빈 목록 + `diagnosisId=null` |
| `GET /recommendations/{id}` | Long path | `RecommendationResponse`: id, diagnosisId, goalId, domain, type, title, description, priority, status, effect(8필드), createdAt, updatedAt | — |
| `GET /{id}/reasons` | | `reasons[]{code, description, evidence}` `reasonId ASC` | 빈 배열 |
| `GET /{id}/effect` | | `monthlyCostSaving, annualCostSaving, interestSaving, riskReduction, measuredAt(=createdAt), assumptions[]`(`ANNUAL_IS_MONTHLY_TIMES_12` 등 생성기가 남긴 가정) | 모두 null이면 `available=false` |
| `GET /{id}/goal-impact` | | `goalId, currentForecastDate, improvedForecastDate, advancedDays, contributionAmount, unavailableReason`(`NO_GOAL`/`SNAPSHOT_WITHOUT_FORECAST`) | goalId null → `unavailableReason=NO_GOAL` |

`/summary`·`/priorities`·`/history`는 `/{recommendationId}`보다 리터럴 우선이라 충돌하지 않지만, path variable을 `Long`으로 두어 문자열 매칭 자체가 400이 되게 한다.

## 5. 549 구현 순서

### 5.1 상태 전이 (`entity/RecommendationStatus` 또는 `service/RecommendationTransitionPolicy`)

| 현재 \ 요청 | accept | reject | complete | defer |
| --- | --- | --- | --- | --- |
| PENDING | ACCEPTED | REJECTED | ✗ | DEFERRED |
| DEFERRED | ACCEPTED | REJECTED | ✗ | ✗ |
| ACCEPTED | ✗ | REJECTED | COMPLETED | DEFERRED |
| REJECTED | ✗ | ✗ | ✗ | ✗ |
| COMPLETED | ✗ | ✗ | ✗ | ✗ |

✗는 `RECOMMENDATION_STATUS_TRANSITION_NOT_ALLOWED`(409). **같은 상태 재요청도 ✗** — 이력 행이 중복 생성되지 않게 하는 "중복 요청 방지" 해석이다.
`GoalService`의 `GOAL_STATUS_TRANSITION_NOT_ALLOWED` 처리 방식을 따른다. REJECTED·COMPLETED는 종료 상태로 재개하지 않는다(재생성은 새 진단에서).

### 5.2 상태 변경 API

`POST /recommendations/{id}/{accept|reject|complete|defer}` Body `{ memo?, reason? }`(각 ≤500, 공백만이면 null 처리) → 200 `RecommendationResponse`.

한 트랜잭션: `RecommendationUserLock`으로 사용자 행 잠금 → 소유 조회(없으면 404) → 전이 검증 → `Recommendation.changeStatus(status, now)`(`updated_at` 갱신) → `RecommendationHistory.of(id, status, reason, memo, now)` INSERT.
`Recommendation` 엔티티에 상태 변경 메서드가 없으면 추가한다(547은 생성만 있음). 하드 삭제·이력 수정 없음.

### 5.3 이력 API

`GET /recommendations/history?startDate&endDate&page&size` → `PageResponse<RecommendationHistoryResponse>{historyId, recommendationId, title, domain, status, reason, memo, changedAt}`.
기간은 `QueryPeriodPolicy` 공통 정책(`endDate` 기본 오늘, 기본 3개월, 최대 12개월, `startDate ≤ endDate`)을 그대로 적용하고 `[from, to+1일)` 반개구간으로 `findOwnedInPeriod`를 호출한다. 정렬 `changedAt DESC, id DESC`.
생성 시 남긴 PENDING 이력도 포함된다(생성 사실 자체가 이력).

## 6. 테스트

H2 `@SpringBootTest` + MockMvc, 기존 `diagnosis`·`recommendation` 테스트 픽스처 재사용.

- 생성기 3종 단위: 실제 진단 스냅샷 고정본으로 효과·근거·키 안정성, `A ≤ 0` → current null·advancedDays null, `R−A ≤ 0`·`remaining ≤ 0`이면 후보 없음, 금액 필드 누락 → `SNAPSHOT_FIELD_MISSING`, 546 병합 후 v3 스냅샷(`forecast` 포함)에서도 같은 결과.
- 우선순위: 동점 tie-break, 시급성 경계(14/30일), 재생성 시 priority 불변.
- 생성 API: 정상, 미완료 진단 409, goal 불일치 400, 타인 진단 404, 인증 없음 401, 두 번 호출 시 `created=0 reused=n`, 문제 0건.
- 조회 API: 소유권 격리(타인 id 404), status enum 검증, size 0/101, summary 0건, priorities 완료 진단 없음, goal-impact NO_GOAL.
- 진단 → 추천 달성일 정합: 같은 목표에 대해 생성기의 `current`가 `GoalCalculationService.getForecast(userId, goalId, createdAt)`의 `expectedAchievementDate`와 같음(같은 식 검증).
- 549: 전이표 25칸 전부(파라미터화), 동일 상태 재요청 409, memo 501자 400, 이력 반개구간·기간 정책·정렬, 타인 추천 404.
- MySQL: 547 `RecommendationMySqlIntegrationTest` 재실행으로 충분(스키마 변경 없음). `RECOMMENDATION_MYSQL_TEST_URL` 없으면 미검증으로 보고.

## 7. 문서와 인계

- `backend/docs/recommendation-api.md` 신규: 엔드포인트·오류코드·효과 계산식·우선순위 점수표·전이표·빈 결과.
- `recommendation.md` §1 "운영 생성기는 없다"와 §9 체크리스트 갱신.
- 남길 제한사항: 대출·카드 추천 부재(진단 규칙 선행), 군집 α=0, ACCEPT 시 `improvement_action` 미생성, 진단·목표 삭제 이후 표시 정책 미정, `annual = monthly × 12` 가정.
- 루트 `docs/` API 명세 변경 없음. 응답 필드는 명세에 없으므로 이 문서와 구현 문서가 계약이다 — 프론트 연동 시 공유 필요.
