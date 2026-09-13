# Recommendation API — 생성·조회 (S15P21D105-548) · 상태변경·이력 (S15P21D105-549)

작성일: 2026-09-13. 547 저장 기반([recommendation.md](recommendation.md)) 위에 API 16의 HTTP 경로 13개를 구현했다.
계획과 결정 배경은 [recommendation-api-plan.md](recommendation-api-plan.md)에 있다.

기준 자료: [API 16](../../docs/API명세서/16%20Recommendation%203cdbb6ea3564801eb1bbc55201aee86a.csv),
[기획안](../../docs/기획안/기획안%20작성%203c3bb6ea35648097a693e0e321b3e9c0.md) 4-5·5-3·5-4·5-6, [진단 API](diagnosis-api.md) §규칙·근거.

## 1. 범위

| 포함 | 제외(후속) |
| --- | --- |
| 운영 생성기 3종(진단이 만드는 3개 문제 유형 대응) | 대출·카드·계좌·예적금 추천 — 대응 진단 규칙 미구현 |
| 효과 계산, 우선순위 점수·순위 | 군집 가중치 α (군집 미연동, α = 0) |
| POST generate, GET 목록·summary·priorities·상세·reasons·effect·goal-impact | ACCEPT 시 `improvement_action` 생성 (Improvement 이슈) |
| POST accept·reject·complete·defer, GET history | Simulation 연동 |

## 2. 생성기와 효과 계산

생성기는 순수 함수이며 **진단이 저장한 값만** 읽는다(현재 데이터로 재계산하지 않는다). 버전 `548.1`. 규칙 의미가 바뀌면 올린다 — generation_key 공간이 갈려 같은 진단에서 새 추천이 만들어진다.
기준일은 진단 스냅샷 `asOf`(접수 시각, 위험 판정에 쓴 날), 없으면 완료일이다.

| 진단 문제 | 추천 domain/type · candidateKey | 읽는 값 | 효과 |
| --- | --- | --- | --- |
| GOAL/SAVING_GOAL_RISK | SAVING / INCREASE_MONTHLY_SAVING · `:<goalId>` | `currentValue.requiredMonthlySaving`(R)·`averageMonthlySaving`(A), `evidence.savingGoalSnapshot.targetAmount`, `evidence.goalSnapshot.currentAmount` | `contributionAmount = R − A`. `remaining = target − current`. `currentForecastDate = base + ceil(remaining/A)`(A ≤ 0 → null), `improvedForecastDate = base + ceil(remaining/R)`, `advancedDays = current − improved`(한쪽 null → null). R − A ≤ 0 또는 remaining = 0 → 후보 없음. 금액 필드 누락 → 날짜 null + 근거 `SNAPSHOT_FIELD_MISSING` |
| GOAL/SPENDING_GOAL_RISK | SPENDING / REDUCE_SPENDING · `:<goalId>` | `currentValue.monthlyBudget·spent·projectedMonthEnd` | `monthlyCostSaving = max(0, max(spent, projected) − budget)`, `annualCostSaving = monthly × 12`(가정 `ANNUAL_IS_MONTHLY_TIMES_12`, 이 생성기만). 0 → 후보 없음 |
| CASHFLOW/BALANCE_SHORTAGE | CASHFLOW / SECURE_SHORTAGE_BUFFER · `:<riskId>` | `currentValue.riskId·riskDate·expectedBalance`, `evidence.forecastId` | 부족액 `max(0, −expectedBalance)`. `riskReduction = BALANCE_SHORTAGE_AVOIDED`, 금액 효과 null |

예상 달성일 식은 `GoalCalculationService.forecast()`와 같다. 현재·개선 달성일을 **같은 식·같은 기준일**로 계산하므로 546이 스냅샷에 넣는 `forecast`는 읽지 않는다(v2·v3 진단 동일 결과).
근거 코드: `GOAL_SAVING_SHORTFALL`, `GOAL_FORECAST_DELAY`, `SNAPSHOT_FIELD_MISSING`, `SPENDING_OVER_BUDGET`, `CASHFLOW_SHORTAGE`.

## 3. 우선순위 (`RecommendationPriorityPolicy`)

| 기준 | 점수 |
| --- | --- |
| 금융 위험도 | 원본 문제 severity CRITICAL 40 / HIGH 30 / MEDIUM 20 / LOW 10 |
| 예상 개선 효과 | max(monthlyCostSaving, contributionAmount)를 10만 원 단위 올림, 최대 30 |
| 목표 기여도 | advancedDays 30일당 5, 최대 20 |
| 시급성 | CASHFLOW `riskDate − 기준일` 14일 이내 20, 30일 이내 10 (지난 날짜도 14일 이내) |
| 실행 난이도 | SPENDING·SAVING +5 |

`priority` = 한 생성 요청 안의 점수 내림차순 순위(1부터), 동점은 issueId 오름차순 → candidateKey. 점수는 저장 컬럼이 없어 생성 응답 `items[].score`에만 노출한다.
재생성으로 기존 행을 돌려받을 때 priority는 갱신하지 않는다. 다른 진단의 priority는 비교하지 않는다 → priorities API는 최신 완료 진단으로 범위를 좁힌다.

## 4. 생성 API

`POST /api/v1/recommendations/generate` Body `{ diagnosisId, goalId? }` → 신규 1건 이상이면 **201**, 전부 재사용·후보 0건이면 **200**.

응답 `{ diagnosisId, goalId, created, reused, items[]{recommendation, score, created}, skipped[]{issueId, issueType, reason} }`.
`goalId` 생략 시 진단의 goalId를 물려받는다. 후보 0건이면 `items` 빈 목록이고 `skipped`가 이유를 드러낸다(문제 0건이면 skipped도 빈 목록).
같은 진단·같은 규칙 버전은 같은 generation_key → 재요청은 `reused`로 기존 행(상태·근거·priority 불변). 동시 요청은 사용자 행 잠금으로 직렬화된다.

| 상황 | 결과 |
| --- | --- |
| diagnosisId 없음·0, goalId ≤ 0 | 400 `VALIDATION_ERROR` |
| 타인·없는 진단, 타인·삭제된 목표 | 404 `RESOURCE_NOT_FOUND` |
| 미완료 진단 | 409 `DIAGNOSIS_NOT_COMPLETED` |
| goalId ≠ 진단 goalId | 400 `RECOMMENDATION_GOAL_MISMATCH` |

## 5. 조회 API

공통: 타인·없는 추천은 404, 저장값만 반환, `@Transactional(readOnly = true)`.

| Endpoint | 파라미터 | 응답 | 정렬·빈 결과 |
| --- | --- | --- | --- |
| `GET /recommendations` | `status?`(enum 외 400), `domain?`(`^[A-Z][A-Z0-9_]{0,49}$`), `page≥0`, `size 1~100` 기본 20 | `PageResponse<Recommendation>` | `createdAt DESC, id DESC` |
| `GET /recommendations/summary` | 없음 | `total, byStatus{5종, 0 포함}, byDomain{}, expectedMonthlyCostSaving, expectedContributionAmount`(PENDING+ACCEPTED 합) | 0으로 채운 객체 |
| `GET /recommendations/priorities` | `goalId?`(타인·삭제 → 404) | `diagnosisId, goalId, recommendations[]` — 최신 완료 진단(goalId 있으면 그 목표의)의 PENDING·DEFERRED를 `priority ASC, id ASC` | 완료 진단 없음 → `diagnosisId=null`, 빈 목록 |
| `GET /recommendations/{id}` | | `recommendationId, diagnosisId, goalId, domain, type, title, description, priority, status, effect{…}, createdAt, updatedAt` | |
| `GET /{id}/reasons` | | `[{reasonId, code, description, evidence}]` `reasonId ASC` | 빈 배열 |
| `GET /{id}/effect` | | `available, 8개 효과 필드, measuredAt(=createdAt), assumptions[]` | 모두 null → `available=false` |
| `GET /{id}/goal-impact` | | `goalId, currentForecastDate, improvedForecastDate, advancedDays, contributionAmount, unavailableReason`(`NO_GOAL` / `NOT_CALCULATED` / null) | |

## 6. 상태 변경·이력 (549)

`POST /recommendations/{id}/{accept|reject|complete|defer}` Body `{ memo?, reason? }`(각 ≤500, 공백만이면 null, 본문 생략 가능) → 200 상세.

| 현재 \ 요청 | accept | reject | complete | defer |
| --- | --- | --- | --- | --- |
| PENDING | ACCEPTED | REJECTED | ✗ | DEFERRED |
| DEFERRED | ACCEPTED | REJECTED | ✗ | ✗ |
| ACCEPTED | ✗ | REJECTED | COMPLETED | DEFERRED |
| REJECTED | ✗ | ✗ | ✗ | ✗ |
| COMPLETED | ✗ | ✗ | ✗ | ✗ |

✗ 및 같은 상태 재요청은 409 `RECOMMENDATION_STATUS_TRANSITION_NOT_ALLOWED`(이력 중복 방지). 한 트랜잭션: 사용자 행 잠금 → 소유 조회 → 전이 검사 → `Recommendation.changeStatus`(`updated_at`) → `recommendation_history` INSERT. 효과·근거·priority는 불변.

`GET /recommendations/history?startDate&endDate&page&size` → `PageResponse<{historyId, recommendationId, title, domain, status, reason, memo, changedAt}>`.
기간은 `QueryPeriodPolicy`(endDate 기본 오늘, 기본 3개월, 최대 12개월, `startDate ≤ endDate`, 위반 시 400), `[start 00:00, end + 1일 00:00)`. 정렬 `changedAt DESC, id DESC`. 생성 시 PENDING 이력도 포함.

## 7. 오류 코드

| 코드 | HTTP |
| --- | --- |
| `DIAGNOSIS_NOT_COMPLETED` | 409 |
| `RECOMMENDATION_GOAL_MISMATCH` | 400 |
| `RECOMMENDATION_STATUS_TRANSITION_NOT_ALLOWED` | 409 |

547의 Reader·Store가 쓰던 `INVALID_REQUEST`는 위 두 코드로 바꿨다.

## 8. 검증

H2 (`.\gradlew.bat test --tests 'com.ssafy.dodam.domain.recommendation.*'`, 2026-09-13 통과, 51건 = 547 38건 + 신규 13건):

- `RecommendationGeneratorTest` 7: 저축 생성기 달성일 계산·속도 0·필드 누락·충분한 속도·데이터 부족, 기준일 fallback, 소비 생성기 초과분·연간 ×12, 현금흐름 생성기 필드·날짜 오류, 우선순위 점수(58/40/16)·동점 tie-break, 전이표 25칸.
- `RecommendationApiTest` 6: **실제 진단 실행**으로 만든 스냅샷에서 생성 → 효과·근거·goal-impact, 재요청 멱등(201→200, reused), goal 불일치 400·타인 404·검증 400·401·문제 0건, 목록 필터·페이지 경계·소유권, summary, priorities 최신 진단 한정·goalId·완료 진단 없음, 전이표·같은 상태 재요청 409·memo 501자 400·이력 4건·효과 불변, 이력 기간 정책·반개구간·정렬·소유권, 줄이기 목표 진단 형식.

MySQL: 스키마 변경 없음. 547 `RecommendationMySqlIntegrationTest` 3건은 `RECOMMENDATION_MYSQL_TEST_URL` 설정 시만 실행된다.

## 9. 현재 제한사항과 인계

- [ ] 대출·카드·계좌·예적금 추천 없음. 진단 규칙(고금리 대출, 저사용 카드, 휴면 계좌 등)이 먼저다. 생성기를 추가하면 Collector가 자동으로 모은다.
- [ ] 군집 α = 0. 545 군집 연동 후 `RecommendationPriorityPolicy`에 보조 점수를 더한다.
- [ ] ACCEPT 시 `improvement_action` 생성은 Improvement 이슈. 여기서는 훅을 두지 않았다.
- [ ] `annualCostSaving = monthly × 12`, 우선순위 가중치, 같은 상태 재요청 409는 서비스 결정이다. 팀 합의가 다르면 생성기 버전(`548.1`)을 올린다.
- [ ] 생성기의 예상 달성일 식은 Goal·Simulation(550)과 각각 복제돼 있다. 세 곳이 같은 식을 쓰는 것은 테스트로 고정했지만 한 곳으로 모으는 정리는 546 병합 후.
- 진단·목표 삭제 이후 기존 추천의 표시 정책은 정하지 않았다(저장 당시 근거 보존, 조회 가능).
- 명세 16에 응답 필드가 없으므로 이 문서가 응답 계약이다. 프론트 연동 시 공유 필요.
